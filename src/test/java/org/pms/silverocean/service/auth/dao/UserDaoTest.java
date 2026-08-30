package org.pms.silverocean.service.auth.dao;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pms.silverocean.database.pms.UserRepo;
import org.pms.silverocean.database.pms.entities.Users;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDaoTest {
    @Mock UserRepo userRepo;

    @Test
    void savingBrandNewUserDoesNotUnboxNullGeneratedId() {
        Users user = Users.builder().email("new@example.com").build();
        when(userRepo.save(user)).thenAnswer(invocation -> {
            Users saved = invocation.getArgument(0);
            saved.setId(501L);
            return saved;
        });

        Users saved = assertDoesNotThrow(() -> new UserDao(userRepo).save(user));

        assertEquals(501L, saved.getId());
    }
}
