package ma.youcode.surevote.domain.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;

/**
 * Represents an Administrator user in the SUREVOTE platform.
 * Extends Utilisateur and adds department-specific information.
 * Administrators manage elections, candidates, colleges, and audit logs.
 */
@Entity
@DiscriminatorValue("ADMIN")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Administrateur extends Utilisateur {

    /**
     * The organizational department this administrator belongs to.
     * Example: "Direction des Affaires Académiques", "RH", "Informatique"
     */
    private String departement;

    /**
     * Convenience constructor to create a fully-initialized Administrateur.
     */
    public Administrateur(String cin, String nom, String prenom, String email,
                          String motDePasse, String departement) {
        super();
        this.setCin(cin);
        this.setNom(nom);
        this.setPrenom(prenom);
        this.setEmail(email);
        this.setMotDePasse(motDePasse);
        this.setRole(RoleUtilisateur.ADMIN);
        this.setEnabled(true);
        this.departement = departement;
    }
}
