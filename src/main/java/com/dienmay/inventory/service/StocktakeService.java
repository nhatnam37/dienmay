package com.dienmay.inventory.service;

import com.dienmay.catalog.entity.Product;
import com.dienmay.catalog.repository.ProductRepository;
import com.dienmay.common.exception.*;
import com.dienmay.inventory.context.*;
import com.dienmay.inventory.dto.*;
import com.dienmay.inventory.entity.*;
import com.dienmay.inventory.repository.*;
import com.dienmay.inventory.security.*;
import com.dienmay.inventory.serial.*;
import com.dienmay.organization.entity.Employee;
import com.dienmay.organization.repository.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly=true)
public class StocktakeService {
    private final Clock clock;
    private final StocktakeRepository stocktakes;
    private final StocktakeLineRepository lines;
    private final ProductRepository products;
    private final InventoryProductQueries choices;
    private final BranchRepository branches;
    private final EmployeeRepository employees;
    private final SerialItemRepository serials;
    private final SerialStateService serialState;
    private final StockService stock;
    private final StocktakeSnapshotReader snapshots;
    private final StocktakeComparison comparison;
    private final SerialNumberRules rules;
    private final InventoryAccess access;
    public StocktakeService(StocktakeRepository stocktakes,StocktakeLineRepository lines,ProductRepository products,InventoryProductQueries choices,
            BranchRepository branches,EmployeeRepository employees,SerialItemRepository serials,SerialStateService serialState,StockService stock,
            StocktakeSnapshotReader snapshots,StocktakeComparison comparison,SerialNumberRules rules,InventoryAccess access, Clock clock) {
        this.clock=clock;
        this.stocktakes=stocktakes;this.lines=lines;this.products=products;this.choices=choices;this.branches=branches;this.employees=employees;
        this.serials=serials;this.serialState=serialState;this.stock=stock;this.snapshots=snapshots;this.comparison=comparison;this.rules=rules;this.access=access;
    }
    @Transactional
    public Long open(Long branchId,InventoryActor actor) {
        Long scope=access.branch(branchId,actor,true);
        var branch=branches.findById(scope).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy chi nhánh."));
        if(!branch.isActive()){throw new BusinessException("Chi nhánh đã ngừng hoạt động.");}
        var rows=snapshots.read(scope,Set.of(),true);
        Stocktake take=new Stocktake();take.setBranch(branch);take.setCreatedBy(employee(actor));take.setCode("TMP-"+UUID.randomUUID());take=stocktakes.saveAndFlush(take);
        take.setCode("KK-"+LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+String.format(Locale.ROOT,"%05d",take.getId()));
        replace(take,baseline(rows));stocktakes.flush();return take.getId();
    }
    @Transactional
    public void reconcile(Long id,InventoryActor actor) {
        Stocktake take=locked(id,actor);requireOpen(take);
        var rows=snapshots.read(take.getBranch().getId(),Set.of(),true);
        // Mốc mới làm mất hiệu lực số đếm cũ; không âm thầm dùng số cũ chốt vào tồn mới.
        replace(take,baseline(rows));stocktakes.flush();
    }
    private List<StocktakeLine> baseline(List<StocktakeSnapshotRow> rows) {
        return rows.stream().filter(StocktakeSnapshotRow::inScope).map(row->{StocktakeLine line=new StocktakeLine();line.setProduct(products.getReferenceById(row.productId()));line.setSerialNo(row.serialNo());line.setSystemQty(row.quantity());line.setSnapshotVersion(row.version());return line;}).toList();
    }
    @Transactional
    public void saveCount(Long id,StocktakeCountForm form,InventoryActor actor) {
        Stocktake take=locked(id,actor);requireOpen(take);
        List<StocktakeLine> old=lines.findAllByStocktakeIdOrderByIdAsc(id);
        List<StocktakeCountLineForm> input=form.getLines()==null?List.of():form.getLines();
        Set<Long> productIds=old.stream().map(line->line.getProduct().getId()).collect(Collectors.toCollection(TreeSet::new));
        input.stream().filter(Objects::nonNull).map(StocktakeCountLineForm::getProductId).filter(Objects::nonNull).forEach(productIds::add);
        var current=snapshots.read(take.getBranch().getId(),productIds,true);comparison.assertUnchanged(old,current);
        Map<Long,Product> productMap=new HashMap<>();products.findAllById(productIds).forEach(product->productMap.put(product.getId(),product));
        Map<Long,List<StocktakeLine>> byProduct=old.stream().filter(line->line.getSerialNo()==null||line.getSystemQty()==1).collect(Collectors.groupingBy(line->line.getProduct().getId()));
        List<String> errors=new ArrayList<>();List<StocktakeLine> prepared=new ArrayList<>();Set<Long> seen=new HashSet<>();Set<String> allNumbers=new HashSet<>();
        Map<StocktakeCountLineForm,Set<String>> scanned=new LinkedHashMap<>();
        for(int index=0;index<input.size();index++){
            var entry=input.get(index);int row=index+1;
            if(entry==null || entry.getProductId()==null){errors.add("Dòng "+row+": vui lòng chọn sản phẩm.");continue;}
            Product product=productMap.get(entry.getProductId());
            if(product==null){errors.add("Dòng "+row+": sản phẩm không tồn tại.");continue;}
            if(!seen.add(product.getId())){errors.add("Dòng "+row+": sản phẩm bị lặp.");continue;}
            String note=trim(entry.getNote());
            if(product.isTracksSerial()){
                Set<String> numbers=new LinkedHashSet<>();String text=entry.getSerialText()==null?"":entry.getSerialText();
                for(String raw:text.split("[,\r\n]+")){
                    if(raw.isBlank()){continue;}var result=rules.validate(raw,product.getSerialType());
                    if(!result.valid()){errors.add("Dòng "+row+": "+result.reason());continue;}
                    if(!numbers.add(result.normalized())||!allNumbers.add(result.normalized())){errors.add("Dòng "+row+": Serial trùng "+result.normalized()+".");}
                }
                scanned.put(entry,numbers);
                for(StocktakeLine expected:byProduct.getOrDefault(product.getId(),List.of())){
                    StocktakeLine line=copy(expected);line.setCountedQty(numbers.contains(line.getSerialNo())?1:0);line.setNote(note);prepared.add(line);
                }
            }else{
                int counted;
                try{counted=Integer.parseInt(entry.getCountedQty()==null?"":entry.getCountedQty().strip());if(counted<0){throw new NumberFormatException();}}
                catch(NumberFormatException exception){errors.add("Dòng "+row+": nhập số lượng nguyên không âm, nhập 0 nếu không thấy hàng.");continue;}
                StocktakeLine line=byProduct.getOrDefault(product.getId(),List.of()).stream().findFirst().map(this::copy).orElseGet(()->{StocktakeLine value=new StocktakeLine();value.setProduct(product);return value;});
                line.setCountedQty(counted);line.setNote(note);prepared.add(line);
            }
        }
        if(!seen.containsAll(byProduct.keySet())){errors.add("Phải nhập kết quả cho tất cả sản phẩm trong mốc kiểm kê; không được bỏ dòng.");}
        var observed=snapshots.lookup(allNumbers);
        Set<String> expectedNumbers=old.stream().filter(line->line.getSerialNo()!=null&&line.getSystemQty()==1).map(StocktakeLine::getSerialNo).collect(Collectors.toSet());
        for(var entry:scanned.entrySet()){
            Product product=productMap.get(entry.getKey().getProductId());
            for(String number:entry.getValue()){
                var known=observed.get(number);
                if(expectedNumbers.contains(number)){
                    if(known!=null&&!known.productId().equals(product.getId())){errors.add("Serial "+number+" thuộc sản phẩm khác.");}
                    continue;
                }
                StocktakeLine report=new StocktakeLine();report.setProduct(product);report.setSerialNo(number);report.setSystemQty(0);report.setCountedQty(1);
                String explanation=known==null?"Serial lạ: không tồn tại trong hệ thống.":!known.branchId().equals(take.getBranch().getId())?"Serial lạ: đang thuộc "+known.branchName()+".":"Ngoài phạm vi kiểm kê: trạng thái "+known.status()+".";
                report.setNote(explanation+(trim(entry.getKey().getNote())==null?"":" "+trim(entry.getKey().getNote())));prepared.add(report);
            }
        }
        if(prepared.isEmpty()){errors.add("Chưa có kết quả kiểm kê. Hãy thêm sản phẩm đếm được hoặc kiểm tra lại chi nhánh.");}
        if(!errors.isEmpty()){throw new StocktakeValidationException(errors);}
        replace(take,prepared);stocktakes.flush();
    }
    @Transactional
    public void close(Long id,InventoryActor actor) {
        Stocktake take=locked(id,actor);requireOpen(take);
        if(!actor.hasRole("ADMIN")&&!actor.hasRole("BRANCH_MANAGER")){throw new AccessDeniedException("Chỉ ADMIN hoặc quản lý chi nhánh được chốt kiểm kê.");}
        Employee approver=employee(actor);List<StocktakeLine> counted=lines.findAllByStocktakeIdOrderByIdAsc(id);
        if(counted.isEmpty()||counted.stream().anyMatch(line->line.getCountedQty()==null)){throw new BusinessException("Chưa nhập đủ kết quả đếm. Vui lòng lưu kết quả trước khi chốt.");}
        Set<Long> ids=counted.stream().map(line->line.getProduct().getId()).collect(Collectors.toCollection(TreeSet::new));
        var current=snapshots.read(take.getBranch().getId(),ids,true);comparison.assertUnchanged(counted,current);
        Map<String,StocktakeSnapshotRow> currentMap=comparison.inScope(current);
        for(StocktakeLine line:counted){
            if(line.getCountedQty()<line.getSystemQty()&&trim(line.getNote())==null){throw new BusinessException("Bắt buộc có lý do cho hàng thiếu: "+line.getProduct().getSku()+(line.getSerialNo()==null?"":" / "+line.getSerialNo()));}
            if(line.getSerialNo()==null){var now=currentMap.get(comparison.key(line));if(now!=null&&line.getCountedQty()<now.reservedQty()){throw new BusinessException("Không thể giảm tồn "+line.getProduct().getSku()+" xuống dưới số lượng đang giữ.");}}
        }
        // Khóa Serial thiếu theo PK tăng dần; không tin entity/cache từ lúc mở phiếu.
        Set<String> missing=counted.stream().filter(line->line.getSerialNo()!=null&&line.getSystemQty()==1&&line.getCountedQty()==0).map(StocktakeLine::getSerialNo).collect(Collectors.toSet());
        Map<String,SerialItem> missingItems=new HashMap<>();
        if(!missing.isEmpty()){
            var selected=serials.findAllBySerialNoIn(missing).stream().sorted(Comparator.comparing(SerialItem::getId)).toList();
            for(var selectedItem:selected){var serial=serials.findByIdForUpdate(selectedItem.getId()).orElseThrow(()->new BusinessException("Serial đã thay đổi."));missingItems.put(serial.getSerialNo(),serial);}
            if(missingItems.size()!=missing.size()){throw new BusinessException("Serial đã thay đổi; vui lòng đối chiếu lại.");}
        }
        for(StocktakeLine line:counted){
            if(line.getSerialNo()!=null){
                if(line.getSystemQty()==1&&line.getCountedQty()==0){serialState.transition(missingItems.get(line.getSerialNo()),SerialStatus.DAMAGED,new TransitionContext("STOCKTAKE",id,ActorType.EMPLOYEE,actor.employeeId(),line.getNote(),null));}
                // Serial lạ/ngoài phạm vi chỉ là báo cáo: không đăng ký, không chuyển chi nhánh/trạng thái.
            }else{
                int delta=line.getCountedQty()-line.getSystemQty();var context=new StockContext("STOCKTAKE",id,ActorType.EMPLOYEE,actor.employeeId(),line.getNote());
                if(delta>0){stock.increase(take.getBranch().getId(),line.getProduct().getId(),delta,context);}
                if(delta<0){stock.decrease(take.getBranch().getId(),line.getProduct().getId(),-delta,context);}
            }
        }
        take.setStatus(StocktakeStatus.CLOSED);take.setClosedBy(approver);take.setClosedAt(LocalDateTime.now(clock));stocktakes.flush();
    }
    private void replace(Stocktake take,List<StocktakeLine> replacement){lines.deleteForStocktake(take.getId());for(var line:replacement){line.setStocktake(take);lines.save(line);}}
    private StocktakeLine copy(StocktakeLine old){StocktakeLine line=new StocktakeLine();line.setProduct(old.getProduct());line.setSerialNo(old.getSerialNo());line.setSystemQty(old.getSystemQty());line.setSnapshotVersion(old.getSnapshotVersion());return line;}
    private Stocktake locked(Long id,InventoryActor actor){access.requireInventory(actor);Stocktake take=stocktakes.findForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy phiếu kiểm kê."));access.receiptBranch(take.getBranch().getId(),actor);return take;}
    private void requireOpen(Stocktake take){if(take.getStatus()!=StocktakeStatus.OPEN){throw new BusinessException("Phiếu đã chốt, không được chốt lại hoặc sửa kết quả.");}}
    private Employee employee(InventoryActor actor){return employees.findById(actor.employeeId()).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy nhân viên thao tác."));}
    private String trim(String value){return value==null||value.isBlank()?null:value.strip();}
    public Page<StocktakeSummary> search(Long branchId,StocktakeStatus status,Pageable pageable,InventoryActor actor){return stocktakes.search(access.branch(branchId,actor,false),status,pageable).map(this::summary);}
    public List<StocktakeProductChoice> productChoices(InventoryActor actor){access.requireInventory(actor);return choices.findStocktakeChoices();}
    public StocktakeDetail getById(Long id,InventoryActor actor){
        access.requireInventory(actor);Stocktake take=stocktakes.findDetail(id).orElseThrow(()->new ResourceNotFoundException("Không tìm thấy phiếu kiểm kê."));access.receiptBranch(take.getBranch().getId(),actor);
        var views=lines.findAllByStocktakeIdOrderByIdAsc(id).stream().map(line->new StocktakeLineView(line.getProduct().getId(),line.getProduct().getSku(),line.getProduct().getName(),line.getSerialNo(),line.getCountedQty(),line.getSystemQty(),line.getNote())).toList();
        return new StocktakeDetail(summary(take),take.getClosedBy()==null?null:take.getClosedBy().getFullName(),take.getClosedAt(),views,
                take.getStatus()==StocktakeStatus.OPEN&&(actor.hasRole("ADMIN")||actor.hasRole("BRANCH_MANAGER")),!views.isEmpty()&&views.stream().allMatch(line->line.countedQty()!=null));
    }
    public StocktakeCountForm getCountForm(Long id,InventoryActor actor){
        var detail=getById(id,actor);if(detail.summary().status()!=StocktakeStatus.OPEN){throw new BusinessException("Phiếu đã chốt.");}
        var grouped=detail.lines().stream().collect(Collectors.groupingBy(StocktakeLineView::productId,LinkedHashMap::new,Collectors.toList()));
        StocktakeCountForm form=new StocktakeCountForm();
        for(var group:grouped.entrySet()){
            var entry=new StocktakeCountLineForm();entry.setProductId(group.getKey());var values=group.getValue();var first=values.getFirst();
            if(first.serialNo()==null){entry.setCountedQty(first.countedQty()==null?null:first.countedQty().toString());}
            else{entry.setSerialText(values.stream().filter(line->Integer.valueOf(1).equals(line.countedQty())).map(StocktakeLineView::serialNo).collect(Collectors.joining("\n")));}
            entry.setNote(values.stream().filter(line->line.systemQty()>0||line.serialNo()==null).map(StocktakeLineView::note).filter(Objects::nonNull).findFirst().orElse(null));form.getLines().add(entry);
        }
        return form;
    }
    private StocktakeSummary summary(Stocktake take){return new StocktakeSummary(take.getId(),take.getCode(),take.getBranch().getId(),take.getBranch().getName(),take.getStatus(),take.getCreatedBy().getFullName(),take.getCreatedAt());}
}
