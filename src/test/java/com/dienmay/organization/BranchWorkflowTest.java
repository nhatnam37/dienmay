package com.dienmay.organization;

import com.dienmay.organization.dto.BranchForm;
import com.dienmay.organization.service.BranchService;
import com.dienmay.inventory.service.TransferService;
import com.dienmay.inventory.security.InventoryActor;
import com.dienmay.support.MySqlIntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class BranchWorkflowTest extends MySqlIntegrationTestBase {
    @Autowired BranchService branches;
    @Autowired TransferService transfers;
    @Autowired JdbcTemplate jdbc;
    @Test @WithMockUser(roles="ADMIN")
    void create_activeBranch_appearsInTransferDestinationAndDisappearsWhenInactive(){
        BranchForm form=new BranchForm();form.setCode("T-"+UUID.randomUUID().toString().substring(0,12));form.setName("Test chi nhánh nhận");
        Long id=branches.create(form).id();InventoryActor admin=new InventoryActor(1L,1L,Set.of("ROLE_ADMIN"));
        try{
            assertThat(transfers.branchChoices(admin)).anyMatch(choice->choice.id().equals(id));
            branches.setActive(id,false);assertThat(transfers.branchChoices(admin)).noneMatch(choice->choice.id().equals(id));
            branches.setActive(id,true);assertThat(transfers.branchChoices(admin)).anyMatch(choice->choice.id().equals(id));
        }finally{jdbc.update("DELETE FROM branch WHERE id=?",id);}
    }
    @Test @WithMockUser(roles="WAREHOUSE")
    void list_nonAdminServiceCall_isForbidden(){assertThatThrownBy(()->branches.list()).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);}
}
