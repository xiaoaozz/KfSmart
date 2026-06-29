package com.smart.kf.service;

import com.smart.kf.model.User;
import com.smart.kf.model.UserNotification;
import com.smart.kf.repository.UserNotificationRepository;
import com.smart.kf.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private UserNotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User recipient;
    private User operator;

    @BeforeEach
    void setUp() {
        recipient = new User();
        recipient.setId(1L);
        recipient.setUsername("alice");

        operator = new User();
        operator.setId(2L);
        operator.setUsername("admin");
    }

    @Test
    void sendNotification_normalPath_savesNotification() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(recipient));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(operator));

        notificationService.sendNotification("alice", "admin", "DELETE_KB", "kb_1", "MyKB");

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(notificationRepository).save(captor.capture());
        UserNotification saved = captor.getValue();

        assertEquals("alice", saved.getRecipientUsername());
        assertEquals("admin", saved.getOperatorUsername());
        assertEquals("DELETE_KB", saved.getActionType());
        assertEquals("kb_1", saved.getResourceId());
        assertEquals("MyKB", saved.getResourceName());
        assertFalse(saved.isRead());
        assertNotNull(saved.getMessage());
    }

    @Test
    void sendNotification_selfNotification_skipped() {
        notificationService.sendNotification("admin", "admin", "UPDATE_KB", "kb_1", "MyKB");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendNotification_nullRecipient_skipped() {
        notificationService.sendNotification(null, "admin", "UPDATE_KB", "kb_1", "MyKB");

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void sendNotification_updateKb_messageContainsResourceName() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(recipient));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(operator));

        notificationService.sendNotification("alice", "admin", "UPDATE_KB", "kb_1", "ProjectDocs");

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(notificationRepository).save(captor.capture());
        assertTrue(captor.getValue().getMessage().contains("ProjectDocs"));
    }

    @Test
    void sendNotification_deleteDocument_messageNotNull() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(recipient));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(operator));

        notificationService.sendNotification("alice", "admin", "DELETE_DOCUMENT", "md5abc", "report.pdf");

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(notificationRepository).save(captor.capture());
        assertNotNull(captor.getValue().getMessage());
    }

    @Test
    void getUserNotifications_delegatesToRepository() {
        UserNotification n1 = new UserNotification();
        n1.setRecipientUsername("alice");
        when(notificationRepository.findByRecipientUsernameOrderByCreatedAtDesc("alice"))
            .thenReturn(List.of(n1));

        List<UserNotification> result = notificationService.getUserNotifications("alice");

        assertEquals(1, result.size());
        verify(notificationRepository).findByRecipientUsernameOrderByCreatedAtDesc("alice");
    }

    @Test
    void getUnreadNotifications_delegatesToRepository() {
        when(notificationRepository.findByRecipientUsernameAndIsReadFalseOrderByCreatedAtDesc("alice"))
            .thenReturn(List.of());

        List<UserNotification> result = notificationService.getUnreadNotifications("alice");

        assertTrue(result.isEmpty());
        verify(notificationRepository).findByRecipientUsernameAndIsReadFalseOrderByCreatedAtDesc("alice");
    }

    @Test
    void getUnreadCount_delegatesToRepository() {
        when(notificationRepository.countByRecipientUsernameAndIsReadFalse("alice")).thenReturn(5L);

        long count = notificationService.getUnreadCount("alice");

        assertEquals(5L, count);
    }
}
