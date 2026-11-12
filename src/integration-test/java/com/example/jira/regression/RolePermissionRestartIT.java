package com.example.jira.regression;

import com.example.jira.JiraApplication;
import com.example.jira.dto.admin.RolePermissionData.ReplaceRequest;
import com.example.jira.repository.*;
import com.example.jira.service.impl.RolePermissionServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import java.util.*;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;

/** Dừng và mở lại toàn bộ Spring Boot trên schema riêng, chứng minh seeder startup giữ empty/custom grant thật. */
class RolePermissionRestartIT {
 @Test void applicationRestartDoesNotRestoreRemovedDefaultGrants()throws Exception{
  Map<String,Supplier<Object>> config=new HashMap<>();
  PostgresRegressionIT.database((name,value)->config.put(name,value));
  String url=(String)config.get("spring.datasource.url").get(),user=(String)config.get("spring.datasource.username").get(),password=(String)config.get("spring.datasource.password").get();
  try(var connection=java.sql.DriverManager.getConnection(url,user,password);var statement=connection.createStatement()){
   try(var r=statement.executeQuery("select current_database()")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("jira_regression");}
   statement.execute("create schema if not exists r2_restart");
  }
  int roleId;
  try(var app=open(url,user,password)){
   var users=app.getBean(UserRepository.class);var roles=app.getBean(UserRoleRepository.class);var grants=app.getBean(RolePermissionRepository.class);
   Fixtures.login(users.findByEmail("admin@jira.com").orElseThrow().getUserId());
   roleId=roles.findByRoleName("DEVELOPER").orElseThrow().getRoleId();assertThat(grants.permissionIds(roleId)).hasSize(3);
   app.getBean(RolePermissionServiceImpl.class).replacePermissions(roleId,new ReplaceRequest(grants.permissionIds(roleId),List.of()));
  }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
  try(var app=open(url,user,password)){
   var grants=app.getBean(RolePermissionRepository.class);
   assertThat(grants.permissionIds(roleId)).isEmpty();
   assertThat(app.getBean(UserRoleRepository.class).findByRoleName("DEVELOPER").orElseThrow().getRoleId()).isEqualTo(roleId);
  }
 }
 /** Datasource chỉ lấy từ cluster đã preflight, command-line properties ghi đè config DB local của app. */
 private ConfigurableApplicationContext open(String url,String user,String password){
  return new SpringApplicationBuilder(JiraApplication.class).run(
   "--server.address=127.0.0.1","--server.port=0","--spring.datasource.url="+url,"--spring.datasource.username="+user,"--spring.datasource.password="+password,
   "--spring.jpa.properties.hibernate.default_schema=r2_restart","--spring.datasource.hikari.schema=r2_restart","--spring.jpa.hibernate.ddl-auto=update",
   "--spring.jpa.show-sql=false","--spring.jpa.open-in-view=false","--spring.sql.init.mode=never",
   "--app.jwt.secret="+SecurityRegressionTest.SECRET,"--cloudinary.cloud-name=smoke-dummy","--cloudinary.api-key=smoke-dummy","--cloudinary.api-secret=smoke-dummy",
   "--spring.mail.host=127.0.0.1","--spring.mail.port=1","--spring.mail.test-connection=false");
 }
}
