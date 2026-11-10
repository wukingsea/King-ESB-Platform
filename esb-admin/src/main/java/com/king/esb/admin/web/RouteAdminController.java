package com.king.esb.admin.web;

import com.king.esb.admin.persistence.entity.RouteRecord;
import com.king.esb.admin.service.RoutePublishService;
import com.king.esb.admin.service.RouteService;
import com.king.esb.admin.web.dto.PageResponse;
import com.king.esb.admin.web.dto.RouteResponse;
import com.king.esb.core.route.RouteDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Route administration: CRUD, status toggling, and publishing to the runtime registry. */
@RestController
@RequestMapping("/admin/api/v1/routes")
public class RouteAdminController {

    private final RouteService routeService;
    private final RoutePublishService publishService;

    public RouteAdminController(RouteService routeService, RoutePublishService publishService) {
        this.routeService = routeService;
        this.publishService = publishService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER','OPERATOR','ADMIN')")
    public PageResponse<RouteResponse> list(@RequestParam(required = false) Boolean enabled,
                                            @PageableDefault(size = 20) Pageable pageable) {
        Page<RouteResponse> page = routeService.list(enabled, pageable).map(RouteResponse::of);
        return PageResponse.of(page);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('VIEWER','OPERATOR','ADMIN')")
    public RouteResponse get(@PathVariable String id) {
        return RouteResponse.of(routeService.get(id));
    }

    @GetMapping("/{id}/definition")
    @PreAuthorize("hasAnyRole('VIEWER','OPERATOR','ADMIN')")
    public RouteDefinition definition(@PathVariable String id) {
        return routeService.getDefinition(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public RouteResponse create(@RequestBody RouteDefinition definition, Authentication authentication) {
        RouteRecord saved = routeService.create(definition, authentication.getName());
        return RouteResponse.of(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public RouteResponse update(@PathVariable String id, @RequestBody RouteDefinition definition,
                                Authentication authentication) {
        return RouteResponse.of(routeService.update(id, definition, authentication.getName()));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public RouteResponse setStatus(@PathVariable String id, @RequestBody StatusRequest request,
                                   Authentication authentication) {
        return RouteResponse.of(routeService.setEnabled(id, request.enabled(), authentication.getName()));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public RouteResponse publish(@PathVariable String id, Authentication authentication) {
        return RouteResponse.of(publishService.publish(id, authentication.getName()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable String id, Authentication authentication) {
        routeService.delete(id, authentication.getName());
        publishService.unpublish(id);
    }

    /** Body for the enable/disable endpoint. */
    public record StatusRequest(boolean enabled) {
    }
}
