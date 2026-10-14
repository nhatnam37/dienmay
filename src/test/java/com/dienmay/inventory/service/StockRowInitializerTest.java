package com.dienmay.inventory.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StockRowInitializerTest {
    @Test
    void ensureExists_concurrentUniqueConflict_retriesInNewTransaction() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), eq(1L), eq(2L))).thenReturn(0, 1);
        when(jdbc.update(anyString(), eq(1L), eq(2L))).thenThrow(new DuplicateKeyException("trùng"));
        new StockRowInitializer(jdbc, manager).ensureExists(1L, 2L);
        verify(manager, times(2)).getTransaction(any());
        verify(manager).rollback(any());
        verify(manager).commit(any());
    }
}
