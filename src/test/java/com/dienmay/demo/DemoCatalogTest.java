package com.dienmay.demo;

import com.dienmay.inventory.serial.SerialNumberRules;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.assertj.core.api.Assertions.*;

class DemoCatalogTest {
    @Test void catalog_assetsAndIdentifiers_areCompleteAndValid()throws Exception{
        var catalog=new DemoCatalog();var rules=new SerialNumberRules();
        var scenarios=new DemoScenarios(null,null,null,null,null,null,null,null,null,rules);
        assertThat(catalog.items()).hasSize(80);assertThat(catalog.items()).extracting(DemoCatalog.Item::sku).doesNotHaveDuplicates();
        assertThat(catalog.items().stream().map(DemoCatalog.Item::category).distinct()).hasSize(10);
        assertThat(catalog.items().stream().map(DemoCatalog.Item::brand).distinct()).hasSize(8);
        assertThat(catalog.items()).anyMatch(i->i.months()==0);
        var serials=new HashSet<String>();
        for(var item:catalog.items()){
            assertThat(item.price().stripTrailingZeros().scale()).isLessThanOrEqualTo(0);
            assertThat(catalog.image(item).getBytes()).startsWith((byte)137,(byte)80,(byte)78,(byte)71);
            if(item.serial())for(int b=0;b<3;b++)for(int n=1;n<=20;n++){
                String serial=scenarios.serial(item,b,n);assertThat(serials.add(serial)).isTrue();
                if(item.type().equals("IMEI")){assertThat(serial).matches("[0-9]{15}");assertThat(rules.isValidLuhn(serial)).isTrue();}
                else assertThat(serial).matches("[A-Z0-9-]+");
            }
        }
    }
}
