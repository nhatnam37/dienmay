package com.dienmay.organization;

import com.dienmay.organization.dto.BranchForm;
import com.dienmay.organization.entity.Branch;
import com.dienmay.organization.repository.BranchRepository;
import com.dienmay.organization.service.BranchService;
import com.dienmay.common.exception.BusinessException;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BranchServiceTest {
    @Mock BranchRepository branches;
    ValidatorFactory factory;
    BranchService service;
    @BeforeEach void setup(){factory=Validation.buildDefaultValidatorFactory();service=new BranchService(branches,factory.getValidator());}
    @AfterEach void close(){factory.close();}
    BranchForm form(){BranchForm f=new BranchForm();f.setCode(" cn-hcm ");f.setName(" Chi nhánh Hồ Chí Minh ");return f;}
    @Test void create_lowercaseCode_normalizesBeforeDuplicateCheckAndSave(){
        when(branches.saveAndFlush(any())).thenAnswer(call->call.getArgument(0));
        var view=service.create(form());assertThat(view.code()).isEqualTo("CN-HCM");assertThat(view.name()).isEqualTo("Chi nhánh Hồ Chí Minh");verify(branches).existsByCodeIgnoreCase("CN-HCM");
    }
    @Test void create_duplicateCode_rejectsWithoutSaving(){
        when(branches.existsByCodeIgnoreCase("CN-HCM")).thenReturn(true);
        assertThatThrownBy(()->service.create(form())).isInstanceOf(BusinessException.class).hasMessageContaining("đã tồn tại");verify(branches,never()).saveAndFlush(any());
    }
    @Test void create_blankName_rejectsAtService(){BranchForm f=form();f.setName(" ");assertThatThrownBy(()->service.create(f)).isInstanceOf(BusinessException.class);verifyNoInteractions(branches);}
    @Test void setActive_existingBranch_preservesRowAndHistoricalReferences(){Branch b=new Branch();when(branches.findById(1L)).thenReturn(Optional.of(b));service.setActive(1L,false);assertThat(b.isActive()).isFalse();verify(branches).saveAndFlush(b);verify(branches,never()).delete(any());}
}
