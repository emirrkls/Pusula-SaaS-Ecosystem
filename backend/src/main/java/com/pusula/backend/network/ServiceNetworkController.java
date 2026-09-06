package com.pusula.backend.network;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import static com.pusula.backend.network.NetworkDtos.*;

@RestController @RequestMapping("/api/service-network")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN','SUPER_ADMIN')")
public class ServiceNetworkController {
    private final ServiceNetworkService service;
    public ServiceNetworkController(ServiceNetworkService service) { this.service=service; }
    @GetMapping("/context") public Context context() { return service.context(); }
    @PutMapping("/policies/{companyId}") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void configure(@PathVariable Long companyId,@Valid @RequestBody PolicyRequest request) { service.configure(companyId,request); }
    @GetMapping("/members") public PageResult<Member> members(@RequestParam(defaultValue="0") int page,@RequestParam(required=false) String query) { return service.listMembers(page,query); }
    @GetMapping("/members/{id}") public Member member(@PathVariable Long id) { return service.getMember(id); }
    @PostMapping("/members/invite") public Member invite(@Valid @RequestBody Invite request) { return service.invite(request); }
    @PostMapping("/members/create") public CreatedChild create(@Valid @RequestBody CreateChild request) { return service.createChild(request); }
    @PostMapping("/members/{id}/decision") public Member decide(@PathVariable Long id,@Valid @RequestBody Decision request) { return service.decideInvite(id,request.accept()); }
    @PostMapping("/members/{id}/close") public Member close(@PathVariable Long id,@Valid @RequestBody Note note) { return service.closeMember(id,note); }
    @GetMapping("/orders") public PageResult<Order> orders(@RequestParam(defaultValue="incoming") String direction,@RequestParam(required=false) String query,
            @RequestParam(required=false) NetworkOrder.Status status,@RequestParam(required=false) LocalDate from,@RequestParam(required=false) LocalDate to,
            @RequestParam(defaultValue="0") int page) { return service.listOrders(direction,query,status,from,to,page); }
    @GetMapping("/orders/{id}") public Order order(@PathVariable Long id) { return service.getOrder(id); }
    @PostMapping("/orders") public Order dispatch(@Valid @RequestBody Dispatch request) { return service.dispatch(request); }
    @PostMapping("/orders/{id}/accept") public Order accept(@PathVariable Long id,@RequestBody Accept request) { return service.accept(id,request); }
    @PostMapping("/orders/{id}/reject") public Order reject(@PathVariable Long id,@Valid @RequestBody Note note) { return service.resolve(id,note,false); }
    @PostMapping("/orders/{id}/cancel") public Order cancel(@PathVariable Long id,@Valid @RequestBody Note note) { return service.resolve(id,note,true); }
    @PostMapping("/orders/{id}/notes") public void note(@PathVariable Long id,@Valid @RequestBody Note note) { service.addNote(id,note); }
    @GetMapping("/orders/{id}/history") public PageResult<Event> history(@PathVariable Long id,@RequestParam(defaultValue="0") int page) { return service.history(id,page); }
}
