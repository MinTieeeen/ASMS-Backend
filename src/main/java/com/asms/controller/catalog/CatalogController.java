package com.asms.controller.catalog;

import com.asms.constant.ApiPaths;
import com.asms.dto.catalog.SchoolListResponse;
import com.asms.service.catalog.SchoolCatalogService;
import com.asms.service.catalog.SchoolCatalogService.ActiveSchools;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * Catalogs every signed-in user reads: schools for the profile picker (API-USER-21). Holidays (API-USER-26) come with
 * the holiday catalog.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Catalogs", description = "Schools and holidays")
public class CatalogController {

    private final SchoolCatalogService schoolCatalogService;

    @GetMapping(ApiPaths.SCHOOLS)
    @Operation(operationId = "listActiveSchools", summary = "Active schools for the school picker (API-USER-21)")
    @ApiResponse(responseCode = "200", description = "Sorted by name; send If-None-Match to get 304 when unchanged")
    public ResponseEntity<SchoolListResponse> listActiveSchools(WebRequest request) {
        ActiveSchools schools = schoolCatalogService.activeSchools();
        if (request.checkNotModified(schools.etag())) {
            return ResponseEntity.status(304).eTag(schools.etag()).build();
        }
        return ResponseEntity.ok()
                .eTag(schools.etag())
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(schools.body());
    }
}
