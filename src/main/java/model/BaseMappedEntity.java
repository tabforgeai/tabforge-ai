package model;

import java.io.Serializable;
import jakarta.persistence.MappedSuperclass;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *   U konkretnoj aplikaciji (JsfProjectTemplate), napraviti klasu koja nasledjuje ovu, i podesava generator value:
  @MappedSuperclass
  public class AppBaseEntity extends BaseMappedEntity {
      @Id
      @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "eracun_seq_id")
      @SequenceGenerator(name = "eracun_seq_id", allocationSize = 1)
      private Integer id;

      @Override
      public Integer getId() { return id; }
      @Override
      public void setId(Integer id) { this.id = id; }
  }
  
  Onda, svaki JPA u konkretnoj aplikaciji nasledjuje  AppBaseEntity
 */
@MappedSuperclass
public abstract class BaseMappedEntity implements Serializable {
    private static final Logger log = LoggerFactory.getLogger(BaseMappedEntity.class);

    public abstract Integer getId();
    public abstract void setId(Integer id);
    
    @Override
    public boolean equals(Object o) {

       // If the object is compared with itself then return true
       if (o == this) {
          return true;
       }

       if (!(o instanceof BaseMappedEntity)) {
          return false;
       }

       // typecast o to Complex so that we can compare data members
       BaseMappedEntity d = (BaseMappedEntity) o;

       if (!d.getClass().getName().equals(this.getClass().getName())) {
          // ako nisu instance iste klase, objekti nisu isti, bez obzira sto su oba instanceof BaseMappedEntity
          return false;
       }

       // Compare the data members and return accordingly
       boolean result = ((this.getId() != null) && (d.getId() != null) &&
                (this.getId().longValue() == d.getId().longValue()));
       return result;
    } 
}