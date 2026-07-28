package com.ailoganalyzer.service.impl;

import com.ailoganalyzer.config.StorageProperties;
import com.ailoganalyzer.domain.AppUser;
import com.ailoganalyzer.domain.LogFile;
import com.ailoganalyzer.domain.LogLevel;
import com.ailoganalyzer.domain.Role;
import com.ailoganalyzer.dto.LogFileSummaryResponse;
import com.ailoganalyzer.exception.InvalidFileException;
import com.ailoganalyzer.exception.ResourceNotFoundException;
import com.ailoganalyzer.repository.LogEntryRepository;
import com.ailoganalyzer.repository.LogFileRepository;
import com.ailoganalyzer.security.AccessControlService;
import com.ailoganalyzer.service.ErrorGroupingService;
import com.ailoganalyzer.service.LogParsingService;
import com.ailoganalyzer.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * LogFileServiceImpl birim testi (Mockito, DB/disk olmadan).
 * Gün 11 sağlamlaştırma: daha önce hiç birim testi olmayan bu servisin
 * (a) dosya doğrulama uç durumlarını (boş/isimsiz/uzantısız/izinsiz dosya),
 * (b) sahiplik atama + role göre listeleme dalını (ADMIN tümü, USER kendi),
 * (c) her erişim yolunda requireAccess çağrıldığını (403 kablolaması) doğrular.
 */
@ExtendWith(MockitoExtension.class)
class LogFileServiceImplTest {

    @Mock private FileStorageService fileStorageService;
    @Mock private LogFileRepository logFileRepository;
    @Mock private LogEntryRepository logEntryRepository;
    @Mock private LogParsingService logParsingService;
    @Mock private ErrorGroupingService errorGroupingService;
    @Mock private AccessControlService accessControl;

    private LogFileServiceImpl service;
    private final StorageProperties storageProperties = new StorageProperties("./uploads", List.of("log", "txt"));

    @BeforeEach
    void setUp() {
        service = new LogFileServiceImpl(fileStorageService, logFileRepository, logEntryRepository,
                storageProperties, logParsingService, errorGroupingService, accessControl);
    }

    private AppUser user(Role role) {
        AppUser u = AppUser.of("kullanici", "hash", role);
        u.setId(UUID.randomUUID());
        return u;
    }

    // --- validate() uç durumları ---

    @Test
    @DisplayName("Boş dosya içeriği → InvalidFileException, hiçbir şey saklanmaz/kaydedilmez")
    void rejectsEmptyContent() {
        assertThatThrownBy(() -> service.ingest("app.log", new byte[0]))
                .isInstanceOf(InvalidFileException.class);
        verify(fileStorageService, never()).store(any(), any());
        verify(logFileRepository, never()).save(any());
    }

    @Test
    @DisplayName("null dosya içeriği → InvalidFileException")
    void rejectsNullContent() {
        assertThatThrownBy(() -> service.ingest("app.log", null))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    @DisplayName("Boş/blank dosya adı → InvalidFileException")
    void rejectsBlankFilename() {
        assertThatThrownBy(() -> service.ingest("   ", "içerik".getBytes()))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    @DisplayName("Uzantısız dosya adı → InvalidFileException")
    void rejectsMissingExtension() {
        assertThatThrownBy(() -> service.ingest("dosya-adi-uzantisiz", "içerik".getBytes()))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    @DisplayName("İzin verilmeyen uzantı (.exe) → InvalidFileException")
    void rejectsDisallowedExtension() {
        assertThatThrownBy(() -> service.ingest("virus.exe", "içerik".getBytes()))
                .isInstanceOf(InvalidFileException.class);
    }

    @Test
    @DisplayName("Büyük harfli izinli uzantı (.LOG) kabul edilir (uzantı küçük harfe çevrilir)")
    void acceptsUppercaseAllowedExtension() {
        when(accessControl.currentUser()).thenReturn(user(Role.USER));
        when(fileStorageService.store(any(), any())).thenReturn("/uploads/app.LOG");
        when(logFileRepository.save(any(LogFile.class))).thenAnswer(inv -> inv.getArgument(0));

        LogFileSummaryResponse response = service.ingest("app.LOG", "2026-01-01 ERROR test".getBytes());

        assertThat(response).isNotNull();
        verify(fileStorageService).store(eq("app.LOG"), any());
    }

    // --- ingest() orkestrasyonu + sahiplik ataması ---

    @Test
    @DisplayName("Yükleme: dosya sahibi aktif kullanıcı olarak atanır, parse + gruplama çağrılır")
    void ingestSetsOwnerAndOrchestratesParsing() {
        AppUser me = user(Role.USER);
        when(accessControl.currentUser()).thenReturn(me);
        when(fileStorageService.store(any(), any())).thenReturn("/uploads/app.log");
        when(logFileRepository.save(any(LogFile.class))).thenAnswer(inv -> inv.getArgument(0));

        service.ingest("app.log", "2026-01-01 ERROR test".getBytes());

        ArgumentCaptor<LogFile> captor = ArgumentCaptor.forClass(LogFile.class);
        verify(logFileRepository).save(captor.capture());
        assertThat(captor.getValue().getOwner()).isEqualTo(me);
        verify(logParsingService).parseAndPersist(eq(captor.getValue()), any());
        verify(errorGroupingService).rebuildGroups(captor.getValue());
    }

    // --- listAll(): role bazlı dal ---

    @Test
    @DisplayName("ADMIN listAll: tüm dosyaları görür (sahiplik filtresi yok)")
    void adminListsAllFiles() {
        when(accessControl.isAdmin()).thenReturn(true);
        LogFile f = new LogFile();
        f.setId(UUID.randomUUID());
        f.setFilename("a.log");
        f.setStatus(com.ailoganalyzer.domain.LogFileStatus.PARSED);
        when(logFileRepository.findAllByOrderByUploadedAtDesc()).thenReturn(List.of(f));

        List<LogFileSummaryResponse> result = service.listAll();

        assertThat(result).hasSize(1);
        verify(logFileRepository, never()).findByOwnerIdOrderByUploadedAtDesc(any());
    }

    @Test
    @DisplayName("USER listAll: yalnızca kendi dosyalarını görür (owner filtreli sorgu)")
    void userListsOnlyOwnFiles() {
        AppUser me = user(Role.USER);
        when(accessControl.isAdmin()).thenReturn(false);
        when(accessControl.currentUserId()).thenReturn(me.getId());
        when(logFileRepository.findByOwnerIdOrderByUploadedAtDesc(me.getId())).thenReturn(List.of());

        List<LogFileSummaryResponse> result = service.listAll();

        assertThat(result).isEmpty();
        verify(logFileRepository, never()).findAllByOrderByUploadedAtDesc();
        verify(logFileRepository).findByOwnerIdOrderByUploadedAtDesc(me.getId());
    }

    // --- getById / getEntries / delete: 404 ve requireAccess kablolaması ---

    @Test
    @DisplayName("getById: bulunamayan dosya → ResourceNotFoundException")
    void getByIdNotFound() {
        UUID id = UUID.randomUUID();
        when(logFileRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getById: bulunan dosyada requireAccess çağrılır; reddedilirse 403 yukarı fırlar")
    void getByIdChecksAccess() {
        UUID id = UUID.randomUUID();
        LogFile file = new LogFile();
        file.setId(id);
        when(logFileRepository.findById(id)).thenReturn(Optional.of(file));
        doThrow(new AccessDeniedException("yetkisiz")).when(accessControl).requireAccess(file);

        assertThatThrownBy(() -> service.getById(id)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("getEntries: bulunamayan dosya → ResourceNotFoundException")
    void getEntriesNotFound() {
        UUID id = UUID.randomUUID();
        when(logFileRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getEntries(id, null)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getEntries: başka kullanıcının dosyasına erişim reddedilirse kayıtlar hiç sorgulanmaz")
    void getEntriesDeniedDoesNotQueryEntries() {
        UUID id = UUID.randomUUID();
        LogFile file = new LogFile();
        file.setId(id);
        when(logFileRepository.findById(id)).thenReturn(Optional.of(file));
        doThrow(new AccessDeniedException("yetkisiz")).when(accessControl).requireAccess(file);

        assertThatThrownBy(() -> service.getEntries(id, LogLevel.ERROR)).isInstanceOf(AccessDeniedException.class);
        verify(logEntryRepository, never()).findByFileIdAndLevelOrderByLineNumberAsc(any(), any());
    }

    @Test
    @DisplayName("delete: bulunamayan dosya → ResourceNotFoundException, diskten silme denenmez")
    void deleteNotFound() {
        UUID id = UUID.randomUUID();
        when(logFileRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(ResourceNotFoundException.class);
        verify(fileStorageService, never()).deleteQuietly(any());
    }

    @Test
    @DisplayName("delete: başka kullanıcının dosyası → 403, DB'den silinmez ve diskten silinmez")
    void deleteDeniedDoesNotDeleteAnything() {
        UUID id = UUID.randomUUID();
        LogFile file = new LogFile();
        file.setId(id);
        file.setStoragePath("/uploads/x.log");
        when(logFileRepository.findById(id)).thenReturn(Optional.of(file));
        doThrow(new AccessDeniedException("yetkisiz")).when(accessControl).requireAccess(file);

        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(AccessDeniedException.class);
        verify(logFileRepository, never()).delete(any());
        verify(fileStorageService, never()).deleteQuietly(any());
    }

    @Test
    @DisplayName("delete: sahip kullanıcı için DB kaydı ve diskteki dosya silinir")
    void deleteRemovesDbRecordAndStoredFile() {
        UUID id = UUID.randomUUID();
        LogFile file = new LogFile();
        file.setId(id);
        file.setStoragePath("/uploads/x.log");
        when(logFileRepository.findById(id)).thenReturn(Optional.of(file));

        service.delete(id);

        verify(accessControl).requireAccess(file);
        verify(logFileRepository).delete(file);
        verify(fileStorageService).deleteQuietly("/uploads/x.log");
    }
}
