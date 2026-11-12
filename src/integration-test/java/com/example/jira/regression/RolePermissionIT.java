package com.example.jira.regression;

import com.example.jira.dto.admin.RolePermissionData.*;
import com.example.jira.entity.*;
import com.example.jira.repository.*;
import com.example.jira.security.*;
import com.example.jira.service.impl.*;
import com.example.jira.config.DatabaseSeeder;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

/** Cùng cluster PG test đã preflight với bộ hồi quy; không chạy create-drop trên DB ứng dụng. */
@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.show-sql=false",
 "app.jwt.secret="+SecurityRegressionTest.SECRET,"app.jwt.expiration=60000"},showSql=false)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@ContextConfiguration(classes=RolePermissionIT.Config.class)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class RolePermissionIT {
 @DynamicPropertySource static void database(DynamicPropertyRegistry registry){PostgresRegressionIT.database(registry);}
 @TestConfiguration
 @org.springframework.boot.autoconfigure.domain.EntityScan("com.example.jira.entity")
 @org.springframework.data.jpa.repository.config.EnableJpaRepositories("com.example.jira.repository")
 @Import({AdminRolePermissionQueryService.class,AdminRoleQueryService.class,RolePermissionServiceImpl.class,
   AdminRoleLock.class,GlobalSecurity.class,ProjectSecurity.class,AuditLogServiceImpl.class,DatabaseSeeder.class})
 static class Config {
  @Bean org.springframework.security.crypto.password.PasswordEncoder encoder(){return new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4);}
 }
 @Autowired JdbcTemplate jdbc; @Autowired EntityManager em; @Autowired PlatformTransactionManager transactions;
 @Autowired UserRepository users; @Autowired UserRoleRepository roles; @Autowired UserRoleAssignmentRepository assignments;
 @Autowired PermissionRepository permissions; @Autowired RolePermissionRepository grants; @Autowired ProjectRepository projects;
 @Autowired ProjectMemberRepository members; @Autowired RolePermissionServiceImpl writer; @Autowired AdminRolePermissionQueryService query;
 @Autowired DatabaseSeeder seeder;
 User actor,second;UserRole target;Permission view,edit,create;
 @BeforeEach void setup(){
  assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("jira_regression");
  jdbc.execute("TRUNCATE users,projects,user_roles,permissions,issue_types,issue_statuses,workflow_rules RESTART IDENTITY CASCADE");
  actor=user("r2-admin-a@test.invalid");second=user("r2-admin-b@test.invalid");
  var sys=roles.saveAndFlush(Fixtures.role(0,"SYSTEM_ADMIN"));admin(actor,sys);admin(second,sys);
  target=roles.saveAndFlush(Fixtures.role(0,"R2_TEST"));view=permission("VIEW_PROJECT");edit=permission("EDIT_ISSUE");create=permission("CREATE_ISSUE");
  Fixtures.login(actor.getUserId());
 }
 @AfterEach void clear(){org.springframework.security.core.context.SecurityContextHolder.clearContext();}
 /** Tập chênh lệch giữ VIEW đã có; empty vẫn hợp lệ, no-op không audit; matrix tách stored và effective. */
 @Test void atomicSetAndSnapshotMetadata(){
  assertThat(writer.replacePermissions(target.getRoleId(),new ReplaceRequest(List.of(),List.of(view.getPermissionId(),edit.getPermissionId()))).permissionIds()).hasSize(2);
  var snapshot=query.snapshot(target.getRoleId());assertThat(snapshot.permissionIds()).contains(view.getPermissionId(),edit.getPermissionId());
  long count=audits();
  writer.replacePermissions(target.getRoleId(),new ReplaceRequest(snapshot.permissionIds(),snapshot.permissionIds()));assertThat(audits()).isEqualTo(count);
  var matrix=query.matrix();
  assertThat(matrix.roles()).filteredOn(r->r.role()==target.getRoleId()).singleElement().satisfies(r->{assertThat(r.scope()).isEqualTo("PROJECT");assertThat(r.editablePermissions()).isTrue();});
  assertThat(matrix.permissions()).filteredOn(p->p.permissionName().equals("VIEW_PROJECT")).singleElement().satisfies(p->assertThat(p.memberAlwaysAllowed()).isTrue());
  writer.replacePermissions(target.getRoleId(),new ReplaceRequest(snapshot.permissionIds(),List.of()));assertThat(query.snapshot(target.getRoleId()).permissionIds()).isEmpty();
 }
 /** Lỗi audit sau delete/insert rollback cả set, không để old grant mất hoặc grant mới tồn tại một nửa. */
 @Test void auditFailureRollsBackWholeSet(){
  writer.replacePermissions(target.getRoleId(),new ReplaceRequest(List.of(),List.of(view.getPermissionId())));
  jdbc.execute("CREATE FUNCTION r2_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='SET_ROLE_PERMISSIONS' THEN RAISE EXCEPTION 'test audit failure'; END IF; RETURN NEW; END $$");
  jdbc.execute("CREATE TRIGGER r2_reject_audit BEFORE INSERT ON audit_logs FOR EACH ROW EXECUTE FUNCTION r2_reject_audit()");
  try{
   assertThatThrownBy(()->writer.replacePermissions(target.getRoleId(),new ReplaceRequest(List.of(view.getPermissionId()),List.of(edit.getPermissionId())))).isInstanceOf(RuntimeException.class);
   assertThat(grants.permissionIds(target.getRoleId())).containsExactly(view.getPermissionId());
  }finally{jdbc.execute("DROP TRIGGER r2_reject_audit ON audit_logs");jdbc.execute("DROP FUNCTION r2_reject_audit()");}
 }
 /** Hai actor admin khác nhau cùng expected: đúng một set/audit thắng; lượt sau409, không union hai desired. */
 @Test void twoAdminsCannotOverwriteSameSnapshot()throws Exception{
  var ids=List.of(actor.getUserId(),second.getUserId());var index=new AtomicInteger();
  var result=race(()->{int i=index.getAndIncrement();Fixtures.login(ids.get(i));return outcome(()->writer.replacePermissions(target.getRoleId(),new ReplaceRequest(List.of(),List.of(i==0?edit.getPermissionId():create.getPermissionId()))));});
  assertThat(result.stream().filter(r->r.failure==null).count()).isEqualTo(1);
  assertThat(result.stream().filter(r->r.failure instanceof com.example.jira.exception.ConflictException).count()).isEqualTo(1);
  assertThat(grants.permissionIds(target.getRoleId())).hasSize(1);assertThat(audits()).isEqualTo(1);
 }
 /** API grant lẻ cũng dùng điểm khóa chung, không chèn được quyền rồi bị PUT snapshot cũ âm thầm ghi đè. */
 @Test void legacyWriterAndReplaceRespectSnapshot()throws Exception{
  var index=new AtomicInteger();
  var result=race(()->{int i=index.getAndIncrement();Fixtures.login(i==0?actor.getUserId():second.getUserId());return outcome(()->{
   if(i==0){writer.addPermissionToRole(target.getRoleId(),edit.getPermissionId());return null;}
   return writer.replacePermissions(target.getRoleId(),new ReplaceRequest(List.of(),List.of(create.getPermissionId())));
  });});
  var stored=grants.permissionIds(target.getRoleId());assertThat(stored).contains(edit.getPermissionId());
  assertThat(stored).allMatch(id->id==edit.getPermissionId()||id==create.getPermissionId());
  assertThat(result.stream().filter(r->r.failure!=null&&!(r.failure instanceof com.example.jira.exception.ConflictException)).count()).isZero();
 }
 /** Sai ID/permission toàn cục/stale/mã trùng không được làm thay đổi set hiện có. */
 @Test void invalidInputAndCatalogCannotPartiallyWrite(){
  var global=permission("DELETE_USER");
  assertThatThrownBy(()->writer.replacePermissions(target.getRoleId(),new ReplaceRequest(List.of(),List.of(999)))).isInstanceOf(com.example.jira.exception.NotFoundException.class);
  assertThatThrownBy(()->writer.replacePermissions(target.getRoleId(),new ReplaceRequest(List.of(),List.of(global.getPermissionId())))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
  permission(" edit_issue ");
  assertThatThrownBy(query::matrix).isInstanceOf(com.example.jira.exception.ConflictException.class);
  assertThatThrownBy(()->writer.replacePermissions(target.getRoleId(),new ReplaceRequest(List.of(),List.of(create.getPermissionId())))).isInstanceOf(com.example.jira.exception.ConflictException.class);
  assertThat(grants.permissionIds(target.getRoleId())).isEmpty();assertThat(audits()).isZero();
 }
 /** User member thường ở hai project nhận cùng grant template, không có quyền trên project thứ ba; lead/SYS vẫn giữ quyền. */
 @Test void grantsApplyOnlyToMembershipProjects(){
  var member=user("r2-member@test.invalid");
  var first=project("R2A");var next=project("R2B");var foreign=project("R2C");
  membership(first,member);membership(next,member);
  var security=new ProjectSecurity(members,grants,projects,new GlobalSecurity(assignments));
  writer.replacePermissions(target.getRoleId(),new ReplaceRequest(List.of(),List.of(edit.getPermissionId())));
  Fixtures.login(member.getUserId());
  tx(()->{assertThat(security.hasPermission(first.getProjectId(),com.example.jira.enums.PermissionName.EDIT_ISSUE)).isTrue();assertThat(security.hasPermission(next.getProjectId(),com.example.jira.enums.PermissionName.EDIT_ISSUE)).isTrue();assertThat(security.hasPermission(foreign.getProjectId(),com.example.jira.enums.PermissionName.VIEW_PROJECT)).isFalse();return null;});
  Fixtures.login(actor.getUserId());writer.replacePermissions(target.getRoleId(),new ReplaceRequest(List.of(edit.getPermissionId()),List.of()));
  Fixtures.login(member.getUserId());
  tx(()->{assertThat(security.hasPermission(first.getProjectId(),com.example.jira.enums.PermissionName.EDIT_ISSUE)).isFalse();assertThat(security.hasPermission(first.getProjectId(),com.example.jira.enums.PermissionName.VIEW_PROJECT)).isTrue();return null;});
  Fixtures.login(actor.getUserId());tx(()->{assertThat(security.hasPermission(first.getProjectId(),com.example.jira.enums.PermissionName.MANAGE_BOARD)).isTrue();return null;});
 }
 /** Seeder nhận biết role mới, không dựa tập empty; run lại giữ role rỗng và quyền tùy chỉnh. */
 @Test void bootstrapOnlyAssignsDefaultsToNewRoles(){
  seeder.run();
  var developer=roles.findByRoleName("DEVELOPER").orElseThrow();assertThat(grants.permissionIds(developer.getRoleId())).hasSize(3);
  var pm=roles.findByRoleName("PROJECT_MANAGER").orElseThrow();
  var before=query.snapshot(pm.getRoleId()).permissionIds();
  writer.replacePermissions(developer.getRoleId(),new ReplaceRequest(grants.permissionIds(developer.getRoleId()),List.of()));
  writer.replacePermissions(pm.getRoleId(),new ReplaceRequest(before,List.of(edit.getPermissionId())));
  seeder.run();
  assertThat(grants.permissionIds(developer.getRoleId())).isEmpty();assertThat(grants.permissionIds(pm.getRoleId())).containsExactly(edit.getPermissionId());
  var missing=permissions.findByPermissionName("CREATE_ISSUE_TYPE").orElseThrow();permissions.deleteById(missing.getPermissionId());
  seeder.run();assertThat(grants.permissionIds(developer.getRoleId())).isEmpty();assertThat(permissions.findByPermissionName("CREATE_ISSUE_TYPE")).isPresent();
 }

 /** Ma trận không ghép role/count snapshot cũ với grant mới khi writer commit giữa các query. */
 @Test void matrixReadsOneConsistentDatabaseSnapshot()throws Exception{
  var queryTarget=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(query);
  var original=org.springframework.test.util.ReflectionTestUtils.getField(queryTarget,"catalog");
  var reached=new CountDownLatch(1);var release=new CountDownLatch(1);
  var factory=new org.springframework.aop.framework.ProxyFactory(original);
  factory.setProxyTargetClass(true);
  factory.addAdvice((org.aopalliance.intercept.MethodInterceptor)invocation->{
   var value=invocation.proceed();
   if(invocation.getMethod().getName().equals("list")){reached.countDown();if(!release.await(15,TimeUnit.SECONDS))throw new IllegalStateException("matrix pause timeout");}
   return value;
  });
  org.springframework.test.util.ReflectionTestUtils.setField(queryTarget,"catalog",factory.getProxy());
  var pool=Executors.newSingleThreadExecutor();
  try{
   var future=pool.submit(()->{Fixtures.login(actor.getUserId());try{return query.matrix();}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}});
   assertThat(reached.await(10,TimeUnit.SECONDS)).isTrue();
   Fixtures.login(second.getUserId());writer.replacePermissions(target.getRoleId(),new ReplaceRequest(List.of(),List.of(edit.getPermissionId())));
   release.countDown();
   var snapshot=future.get(15,TimeUnit.SECONDS);
   assertThat(snapshot.roles()).filteredOn(r->r.role()==target.getRoleId()).singleElement().satisfies(r->assertThat(r.permissionIds()).isEmpty());
  }finally{release.countDown();org.springframework.test.util.ReflectionTestUtils.setField(queryTarget,"catalog",original);pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
  assertThat(query.snapshot(target.getRoleId()).permissionIds()).containsExactly(edit.getPermissionId());
 }
 /** Grant legacy không hợp lệ vẫn hiện stored; chỉ PUT xác nhận desired sạch mới gỡ, permission gốc giữ nguyên. */
 @Test void legacyGrantCanBeExplicitlyRemovedWithoutDeletingPermission(){
  var global=permission("DELETE_USER");
  tx(()->{var g=new RolePermission();g.setId(new RolePermissionId(target.getRoleId(),global.getPermissionId()));g.setRole(em.getReference(UserRole.class,target.getRoleId()));g.setPermission(em.getReference(Permission.class,global.getPermissionId()));em.persist(g);return null;});
  assertThat(query.snapshot(target.getRoleId()).permissionIds()).containsExactly(global.getPermissionId());
  writer.replacePermissions(target.getRoleId(),new ReplaceRequest(List.of(global.getPermissionId()),List.of()));
  assertThat(grants.permissionIds(target.getRoleId())).isEmpty();assertThat(permissions.existsById(global.getPermissionId())).isTrue();
 }

 private User user(String email){var u=Fixtures.user(0);u.setEmail(email);return users.saveAndFlush(u);}
 private Permission permission(String name){var p=new Permission();p.setPermissionName(name);return permissions.saveAndFlush(p);}
 private void admin(User user,UserRole role){var a=new UserRoleAssignment();a.setId(new UserRoleAssignmentId(user.getUserId(),role.getRoleId()));a.setUser(user);a.setRole(role);assignments.saveAndFlush(a);}
 private Project project(String key){var p=Fixtures.project(0);p.setProjectKey(key);p.setLead(actor);return projects.saveAndFlush(p);}
 private void membership(Project p,User u){var m=new ProjectMember();m.setId(new ProjectMemberId(p.getProjectId(),u.getUserId()));m.setProject(p);m.setUser(u);m.setRole(target);members.saveAndFlush(m);}
 private long audits(){return jdbc.queryForObject("select count(*) from audit_logs where action='SET_ROLE_PERMISSIONS'",Long.class);}
 private <T>T tx(Supplier<T> action){return new TransactionTemplate(transactions).execute(status->action.get());}
 private record Outcome(Object value,RuntimeException failure){}
 private Outcome outcome(Supplier<?> f){try{return new Outcome(f.get(),null);}catch(RuntimeException e){return new Outcome(null,e);}}
 private List<Outcome> race(Supplier<Outcome> action)throws Exception{var pool=Executors.newFixedThreadPool(2);try{var a=pool.submit(()->{try{return action.get();}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}});var b=pool.submit(()->{try{return action.get();}finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}});return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));}finally{pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}}
}
