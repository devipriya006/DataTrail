package com.datatrail.backend.entity;



import jakarta.persistence.*;

@Entity
@Table(name="roles")
public class Role {

    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long roleId;

    @Column(nullable=false, unique=true, length=30)
    private String roleName;

    @Column(columnDefinition="TEXT")
    private String description;

   public Role(String roleName, String description){
    this.roleName = roleName;
    this.description = description;
   }
   
   public Long getRoleId() {
    return roleId;
   }
  
   public void setRoleId(Long roleId){
    this.roleId=roleId;
   }

   public String getRoleName(){
    return roleName;
   }

   public void setRoleName(String roleName){
    this.roleName=roleName;
   }

   public String getDescription(){
    return description;
   }

   public void setDescription(String description){
    this.description=description;
   }
}
