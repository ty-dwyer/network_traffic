package com.example.networktraffic.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;

import com.example.networktraffic.entities.IngestionJob;
import com.example.networktraffic.repositories.NetworkRepository;
import com.example.networktraffic.services.PacketIngestionService;

@ExtendWith(MockitoExtension.class)
class PacketControllerTest {
    @Mock NetworkRepository networkRepository;
    @Mock PacketIngestionService ingestionService;

    @Test
    void searchClampsPageSizeAndPassesFiltersToRepository() {
        when(networkRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());
        PacketController controller = new PacketController(networkRepository, ingestionService);

        Page<?> result = controller.searchPackets("10.0.0.1", "10.0.0.2", null, null, null, -1, 1000);

        assertThat(result).isEmpty();
        org.mockito.ArgumentCaptor<Pageable> pageable = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(networkRepository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("timeStamp")).isNotNull();
    }

    @Test
    void rejectsEmptyUploadWithoutStartingAJob() throws Exception {
        PacketController controller = new PacketController(networkRepository, ingestionService);

        var response = controller.uploadAndIngest(new MockMultipartFile("file", "empty.pcap", "application/octet-stream", new byte[0]));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(ingestionService, never()).startIngestion(any(), any(), anyBoolean());
    }

    @Test
    void uploadReturnsAcceptedJobLocation() throws Exception {
        IngestionJob job = new IngestionJob();
        job.setId(42L);
        when(ingestionService.startIngestion(any(), any(), anyBoolean())).thenReturn(job);
        PacketController controller = new PacketController(networkRepository, ingestionService);

        var response = controller.uploadAndIngest(new MockMultipartFile("file", "capture.pcap", "application/octet-stream", new byte[] {1, 2, 3}));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getHeaders().getLocation().toString()).isEqualTo("/ingestion-jobs/42");
        assertThat(response.getBody()).contains("42");
        org.mockito.ArgumentCaptor<String> path = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(ingestionService).startIngestion(path.capture(), any(), org.mockito.ArgumentMatchers.eq(true));
        Files.deleteIfExists(Path.of(path.getValue()));
    }
}
