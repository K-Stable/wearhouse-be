package com.wearhouse.common.global.transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class TransactionAnnotationsTest {

    @Test
    void readTxShouldUseReadOnlyAndSupportsPropagation() {
        Transactional transactional = ReadTx.class.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertTrue(transactional.readOnly());
        assertEquals(Propagation.SUPPORTS, transactional.propagation());
    }

    @Test
    void writeTxShouldUseRequiredPropagationAndWritableTransaction() {
        Transactional transactional = WriteTx.class.getAnnotation(Transactional.class);
        assertNotNull(transactional);
        assertFalse(transactional.readOnly());
        assertEquals(Propagation.REQUIRED, transactional.propagation());
    }
}
