package com.dienmay;

import org.junit.jupiter.api.Test;
import com.dienmay.support.MySqlIntegrationTestBase;

class DienmayApplicationIT extends MySqlIntegrationTestBase {

	@org.springframework.beans.factory.annotation.Autowired org.springframework.context.ApplicationContext context;
	@org.junit.jupiter.api.DisplayName("Ứng dụng khởi động với đầy đủ service nghiệp vụ và migration")
	@Test
	void context_applicationStarts_requiredServicesAvailable() {
        org.assertj.core.api.Assertions.assertThat(context.getBean(com.dienmay.sales.service.CheckoutService.class)).isNotNull();
        org.assertj.core.api.Assertions.assertThat(context.getBean(com.dienmay.inventory.serial.SerialStateService.class)).isNotNull();
		org.assertj.core.api.Assertions.assertThat(context.getBeansOfType(com.dienmay.demo.DemoDataLoader.class)).isEmpty();
		org.assertj.core.api.Assertions.assertThat(context.getBeansOfType(com.dienmay.demo.DemoClock.class)).isEmpty();
		var jdbc=context.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
		org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'demo_seed_%'",Integer.class)).isZero();
	}

}
