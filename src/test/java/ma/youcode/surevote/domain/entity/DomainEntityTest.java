package ma.youcode.surevote.domain.entity;

import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.domain.enums.StatutElection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainEntityTest {

    // ==================== Utilisateur ====================

    @Nested
    class UtilisateurTest {
        @Test
        void getAuthorities_shouldReturnRolePrefixedAuthority() {
            Electeur user = new Electeur();
            user.setRole(RoleUtilisateur.ELECTEUR);
            assertThat(user.getAuthorities()).hasSize(1);
            assertThat(user.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_ELECTEUR");
        }

        @Test
        void getPassword_shouldReturnMotDePasse() {
            Electeur user = new Electeur();
            user.setMotDePasse("hashed-pwd");
            assertThat(user.getPassword()).isEqualTo("hashed-pwd");
        }

        @Test
        void getUsername_shouldReturnEmail() {
            Electeur user = new Electeur();
            user.setEmail("test@example.com");
            assertThat(user.getUsername()).isEqualTo("test@example.com");
        }

        @Test
        void accountNonExpired_shouldBeTrue() {
            assertThat(new Electeur().isAccountNonExpired()).isTrue();
        }

        @Test
        void accountNonLocked_shouldBeTrue() {
            assertThat(new Electeur().isAccountNonLocked()).isTrue();
        }

        @Test
        void credentialsNonExpired_shouldBeTrue() {
            assertThat(new Electeur().isCredentialsNonExpired()).isTrue();
        }

        @Test
        void isEnabled_defaultTrue() {
            Electeur user = new Electeur();
            user.setEnabled(true);
            assertThat(user.isEnabled()).isTrue();
        }

        @Test
        void equals_sameId_shouldBeEqual() {
            Electeur a = new Electeur();
            a.setId(1L);
            Electeur b = new Electeur();
            b.setId(1L);
            assertThat(a).isEqualTo(b);
        }

        @Test
        void equals_differentId_shouldNotBeEqual() {
            Electeur a = new Electeur();
            a.setId(1L);
            Electeur b = new Electeur();
            b.setId(2L);
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_sameInstance_shouldBeEqual() {
            Electeur a = new Electeur();
            assertThat(a).isEqualTo(a);
        }

        @Test
        void equals_differentType_shouldNotBeEqual() {
            Electeur a = new Electeur();
            a.setId(1L);
            assertThat(a).isNotEqualTo("not a user");
        }

        @Test
        void hashCode_sameId_shouldBeEqual() {
            Electeur a = new Electeur();
            a.setId(1L);
            Electeur b = new Electeur();
            b.setId(1L);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void toString_shouldContainClassNameAndEmail() {
            Electeur user = new Electeur();
            user.setId(1L);
            user.setEmail("test@example.com");
            user.setRole(RoleUtilisateur.ELECTEUR);
            assertThat(user.toString()).contains("Electeur").contains("test@example.com");
        }
    }

    // ==================== Electeur ====================

    @Nested
    class ElecteurTest {
        private Electeur electeur;

        @BeforeEach
        void setUp() {
            electeur = new Electeur();
        }

        @Test
        void assignOtp_shouldSetFieldsCorrectly() {
            electeur.assignOtp("123456", 5);
            assertThat(electeur.getOtpCode()).isEqualTo("123456");
            assertThat(electeur.getOtpExpiry()).isAfter(LocalDateTime.now().plusMinutes(4));
            assertThat(electeur.isOtpVerified()).isFalse();
        }

        @Test
        void clearOtp_shouldResetFields() {
            electeur.assignOtp("123456", 5);
            electeur.clearOtp();
            assertThat(electeur.getOtpCode()).isNull();
            assertThat(electeur.getOtpExpiry()).isNull();
            assertThat(electeur.isOtpVerified()).isTrue();
        }

        @Test
        void isOtpValid_withValidOtp_returnsTrue() {
            electeur.assignOtp("123456", 5);
            assertThat(electeur.isOtpValid()).isTrue();
        }

        @Test
        void isOtpValid_withNullOtp_returnsFalse() {
            assertThat(electeur.isOtpValid()).isFalse();
        }

        @Test
        void isOtpValid_withExpiredOtp_returnsFalse() {
            electeur.setOtpCode("123456");
            electeur.setOtpExpiry(LocalDateTime.now().minusMinutes(1));
            assertThat(electeur.isOtpValid()).isFalse();
        }

        @Test
        void hasVotedIn_withMatchingEmargement_returnsTrue() {
            Election election = Election.builder().id(1L).build();
            Emargement emargement = Emargement.builder().election(election).build();
            electeur.setEmargements(new ArrayList<>(List.of(emargement)));

            assertThat(electeur.hasVotedIn(1L)).isTrue();
        }

        @Test
        void hasVotedIn_withNoMatchingEmargement_returnsFalse() {
            Election election = Election.builder().id(1L).build();
            Emargement emargement = Emargement.builder().election(election).build();
            electeur.setEmargements(new ArrayList<>(List.of(emargement)));

            assertThat(electeur.hasVotedIn(2L)).isFalse();
        }

        @Test
        void hasVotedIn_withEmptyEmargements_returnsFalse() {
            electeur.setEmargements(new ArrayList<>());
            assertThat(electeur.hasVotedIn(1L)).isFalse();
        }
    }

    // ==================== Election ====================

    @Nested
    class ElectionTest {
        private Election election;

        @BeforeEach
        void setUp() {
            election = Election.builder()
                    .id(1L)
                    .titre("Test Election")
                    .statut(StatutElection.BROUILLON)
                    .build();
        }

        @Test
        void planifier_fromBrouillon_shouldTransition() {
            election.planifier();
            assertThat(election.getStatut()).isEqualTo(StatutElection.PLANIFIEE);
        }

        @Test
        void planifier_fromNonBrouillon_shouldThrow() {
            election.setStatut(StatutElection.OUVERTE);
            assertThatThrownBy(election::planifier).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void ouvrir_fromPlanifiee_shouldTransition() {
            election.setStatut(StatutElection.PLANIFIEE);
            election.ouvrir();
            assertThat(election.getStatut()).isEqualTo(StatutElection.OUVERTE);
        }

        @Test
        void ouvrir_fromNonPlanifiee_shouldThrow() {
            assertThatThrownBy(election::ouvrir).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void cloturer_fromOuverte_shouldTransition() {
            election.setStatut(StatutElection.OUVERTE);
            election.cloturer();
            assertThat(election.getStatut()).isEqualTo(StatutElection.CLOTUREE);
        }

        @Test
        void cloturer_fromNonOuverte_shouldThrow() {
            assertThatThrownBy(election::cloturer).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void publier_fromCloturee_shouldTransition() {
            election.setStatut(StatutElection.CLOTUREE);
            election.publier();
            assertThat(election.getStatut()).isEqualTo(StatutElection.PUBLIEE);
        }

        @Test
        void publier_fromNonCloturee_shouldThrow() {
            assertThatThrownBy(election::publier).isInstanceOf(IllegalStateException.class);
        }

        @Test
        void isOuverte_correctState() {
            election.setStatut(StatutElection.OUVERTE);
            assertThat(election.isOuverte()).isTrue();
            election.setStatut(StatutElection.BROUILLON);
            assertThat(election.isOuverte()).isFalse();
        }

        @Test
        void isPubliee_correctState() {
            election.setStatut(StatutElection.PUBLIEE);
            assertThat(election.isPubliee()).isTrue();
            election.setStatut(StatutElection.BROUILLON);
            assertThat(election.isPubliee()).isFalse();
        }

        @Test
        void isCloturee_correctState() {
            election.setStatut(StatutElection.CLOTUREE);
            assertThat(election.isCloturee()).isTrue();
            election.setStatut(StatutElection.BROUILLON);
            assertThat(election.isCloturee()).isFalse();
        }

        @Test
        void getTotalVotes_withNull_returnsZero() {
            election.setVotes(null);
            assertThat(election.getTotalVotes()).isEqualTo(0);
        }

        @Test
        void getTotalVotes_withList() {
            election.setVotes(List.of(new Vote(), new Vote()));
            assertThat(election.getTotalVotes()).isEqualTo(2);
        }

        @Test
        void getTotalParticipants_withNull_returnsZero() {
            election.setEmargements(null);
            assertThat(election.getTotalParticipants()).isEqualTo(0);
        }

        @Test
        void getTotalParticipants_withList() {
            election.setEmargements(List.of(new Emargement(), new Emargement()));
            assertThat(election.getTotalParticipants()).isEqualTo(2);
        }

        @Test
        void onCreate_setsTimestampsAndDefaultStatus() {
            Election e = new Election();
            e.onCreate();
            assertThat(e.getDateCreation()).isNotNull();
            assertThat(e.getDateModification()).isNotNull();
            assertThat(e.getStatut()).isEqualTo(StatutElection.BROUILLON);
        }

        @Test
        void onCreate_preservesExistingStatus() {
            Election e = new Election();
            e.setStatut(StatutElection.OUVERTE);
            e.onCreate();
            assertThat(e.getStatut()).isEqualTo(StatutElection.OUVERTE);
        }

        @Test
        void onUpdate_setsModificationTimestamp() {
            Election e = new Election();
            e.onUpdate();
            assertThat(e.getDateModification()).isNotNull();
        }

        @Test
        void equals_sameId_shouldBeEqual() {
            Election a = Election.builder().id(1L).build();
            Election b = Election.builder().id(1L).build();
            assertThat(a).isEqualTo(b);
        }

        @Test
        void equals_differentId_shouldNotBeEqual() {
            Election a = Election.builder().id(1L).build();
            Election b = Election.builder().id(2L).build();
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_sameInstance() {
            assertThat(election).isEqualTo(election);
        }

        @Test
        void equals_differentType() {
            assertThat(election).isNotEqualTo("string");
        }

        @Test
        void toString_containsTitleAndStatut() {
            assertThat(election.toString()).contains("Test Election").contains("BROUILLON");
        }
    }

    // ==================== Candidat ====================

    @Nested
    class CandidatTest {
        @Test
        void getNomComplet_shouldFormatCorrectly() {
            Candidat c = Candidat.builder().nom("dupont").prenom("Jean").build();
            assertThat(c.getNomComplet()).isEqualTo("Jean DUPONT");
        }

        @Test
        void getTotalVotes_withNull_returnsZero() {
            Candidat c = Candidat.builder().build();
            c.setVotes(null);
            assertThat(c.getTotalVotes()).isEqualTo(0L);
        }

        @Test
        void getTotalVotes_withList() {
            Candidat c = Candidat.builder().build();
            c.setVotes(List.of(new Vote(), new Vote(), new Vote()));
            assertThat(c.getTotalVotes()).isEqualTo(3L);
        }

        @Test
        void equals_sameId() {
            Candidat a = Candidat.builder().id(1L).build();
            Candidat b = Candidat.builder().id(1L).build();
            assertThat(a).isEqualTo(b);
        }

        @Test
        void equals_differentId() {
            Candidat a = Candidat.builder().id(1L).build();
            Candidat b = Candidat.builder().id(2L).build();
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_sameInstance() {
            Candidat a = Candidat.builder().id(1L).build();
            assertThat(a).isEqualTo(a);
        }

        @Test
        void equals_differentType() {
            Candidat a = Candidat.builder().id(1L).build();
            assertThat(a).isNotEqualTo("string");
        }

        @Test
        void toString_containsDetails() {
            Election e = Election.builder().id(10L).build();
            Candidat c = Candidat.builder().id(1L).nom("Doe").prenom("John").affiliationOuParti("Party").election(e).build();
            assertThat(c.toString()).contains("Doe").contains("John").contains("10");
        }

        @Test
        void toString_nullElection() {
            Candidat c = Candidat.builder().id(1L).nom("Doe").prenom("John").build();
            assertThat(c.toString()).contains("null");
        }
    }

    // ==================== Vote ====================

    @Nested
    class VoteTest {
        @Test
        void onPersist_setsHorodatageWhenNull() {
            Vote v = new Vote();
            v.onPersist();
            assertThat(v.getHorodatage()).isNotNull();
        }

        @Test
        void onPersist_doesNotOverrideExistingHorodatage() {
            LocalDateTime fixed = LocalDateTime.of(2025, 1, 1, 12, 0);
            Vote v = new Vote();
            v.setHorodatage(fixed);
            v.onPersist();
            assertThat(v.getHorodatage()).isEqualTo(fixed);
        }

        @Test
        void equals_sameId() {
            Vote a = Vote.builder().id(1L).build();
            Vote b = Vote.builder().id(1L).build();
            assertThat(a).isEqualTo(b);
        }

        @Test
        void equals_nullId() {
            Vote a = Vote.builder().build();
            Vote b = Vote.builder().id(1L).build();
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_sameInstance() {
            Vote a = Vote.builder().id(1L).build();
            assertThat(a).isEqualTo(a);
        }

        @Test
        void equals_differentType() {
            Vote a = Vote.builder().id(1L).build();
            assertThat(a).isNotEqualTo("string");
        }

        @Test
        void hashCode_isClassBased() {
            Vote a = Vote.builder().id(1L).build();
            Vote b = Vote.builder().id(2L).build();
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        void toString_containsId() {
            Vote v = Vote.builder().id(5L).build();
            v.setHorodatage(LocalDateTime.now());
            assertThat(v.toString()).contains("5");
        }
    }

    // ==================== Emargement ====================

    @Nested
    class EmargementTest {
        @Test
        void equals_sameId() {
            Emargement a = Emargement.builder().id(1L).build();
            Emargement b = Emargement.builder().id(1L).build();
            assertThat(a).isEqualTo(b);
        }

        @Test
        void equals_differentId() {
            Emargement a = Emargement.builder().id(1L).build();
            Emargement b = Emargement.builder().id(2L).build();
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_sameInstance() {
            Emargement a = Emargement.builder().id(1L).build();
            assertThat(a).isEqualTo(a);
        }

        @Test
        void equals_differentType() {
            Emargement a = Emargement.builder().id(1L).build();
            assertThat(a).isNotEqualTo("string");
        }

        @Test
        void toString_containsId() {
            Emargement e = Emargement.builder().id(1L).recuCryptographique("uuid").dateEmargement(LocalDateTime.now()).build();
            assertThat(e.toString()).contains("1").contains("uuid");
        }

        @Test
        void toString_nullElection() {
            Emargement e = Emargement.builder().id(1L).recuCryptographique("uuid").build();
            assertThat(e.toString()).contains("null");
        }
    }

    // ==================== CollegeElectoral ====================

    @Nested
    class CollegeElectoralTest {
        @Test
        void getTailleCollege_withNull_returnsZero() {
            CollegeElectoral c = CollegeElectoral.builder().build();
            c.setElecteurs(null);
            assertThat(c.getTailleCollege()).isEqualTo(0);
        }

        @Test
        void getTailleCollege_withList() {
            CollegeElectoral c = CollegeElectoral.builder().build();
            c.setElecteurs(List.of(new Electeur(), new Electeur()));
            assertThat(c.getTailleCollege()).isEqualTo(2);
        }

        @Test
        void containsElecteur_withNull_returnsFalse() {
            CollegeElectoral c = CollegeElectoral.builder().build();
            c.setElecteurs(null);
            assertThat(c.containsElecteur(1L)).isFalse();
        }

        @Test
        void containsElecteur_withMatchingId_returnsTrue() {
            Electeur e = new Electeur();
            e.setId(1L);
            CollegeElectoral c = CollegeElectoral.builder().build();
            c.setElecteurs(List.of(e));
            assertThat(c.containsElecteur(1L)).isTrue();
        }

        @Test
        void containsElecteur_withNonMatchingId_returnsFalse() {
            Electeur e = new Electeur();
            e.setId(1L);
            CollegeElectoral c = CollegeElectoral.builder().build();
            c.setElecteurs(List.of(e));
            assertThat(c.containsElecteur(99L)).isFalse();
        }

        @Test
        void equals_sameId() {
            CollegeElectoral a = CollegeElectoral.builder().id(1L).build();
            CollegeElectoral b = CollegeElectoral.builder().id(1L).build();
            assertThat(a).isEqualTo(b);
        }

        @Test
        void equals_differentId() {
            CollegeElectoral a = CollegeElectoral.builder().id(1L).build();
            CollegeElectoral b = CollegeElectoral.builder().id(2L).build();
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void equals_sameInstance() {
            CollegeElectoral a = CollegeElectoral.builder().id(1L).build();
            assertThat(a).isEqualTo(a);
        }

        @Test
        void equals_differentType() {
            CollegeElectoral a = CollegeElectoral.builder().id(1L).build();
            assertThat(a).isNotEqualTo("string");
        }

        @Test
        void toString_containsNom() {
            CollegeElectoral c = CollegeElectoral.builder().id(1L).nom("CS Students").build();
            assertThat(c.toString()).contains("CS Students");
        }
    }

    // ==================== LogAudit ====================

    @Nested
    class LogAuditTest {
        @Test
        void of_createsInstanceWithCorrectFields() {
            LogAudit log = LogAudit.of("LOGIN_SUCCESS", "User logged in", "127.0.0.1", 1L, "user@test.com");
            assertThat(log.getActionType()).isEqualTo("LOGIN_SUCCESS");
            assertThat(log.getDetails()).isEqualTo("User logged in");
            assertThat(log.getAdresseIp()).isEqualTo("127.0.0.1");
            assertThat(log.getUtilisateurId()).isEqualTo(1L);
            assertThat(log.getUtilisateurEmail()).isEqualTo("user@test.com");
            assertThat(log.getDateAction()).isNotNull();
        }

        @Test
        void system_createsSystemLevelLog() {
            LogAudit log = LogAudit.system("SYSTEM_STARTUP", "System started", "0.0.0.0");
            assertThat(log.getUtilisateurId()).isNull();
            assertThat(log.getUtilisateurEmail()).isEqualTo("SYSTEM");
        }

        @Test
        void onUpdate_throwsUnsupportedOperationException() {
            LogAudit log = LogAudit.of("TEST", "test", "127.0.0.1", null, null);
            assertThatThrownBy(log::onUpdate).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void toString_containsActionType() {
            LogAudit log = LogAudit.of("LOGIN_SUCCESS", "details", "127.0.0.1", 1L, "admin@test.com");
            assertThat(log.toString()).contains("LOGIN_SUCCESS");
        }
    }

    // ==================== PasswordResetToken ====================

    @Nested
    class PasswordResetTokenTest {
        @Test
        void isValid_notExpiredNotUsed_returnsTrue() {
            PasswordResetToken token = PasswordResetToken.builder()
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .used(false)
                    .build();
            assertThat(token.isValid()).isTrue();
        }

        @Test
        void isValid_expired_returnsFalse() {
            PasswordResetToken token = PasswordResetToken.builder()
                    .expiresAt(LocalDateTime.now().minusMinutes(1))
                    .used(false)
                    .build();
            assertThat(token.isValid()).isFalse();
        }

        @Test
        void isValid_used_returnsFalse() {
            PasswordResetToken token = PasswordResetToken.builder()
                    .expiresAt(LocalDateTime.now().plusHours(1))
                    .used(true)
                    .build();
            assertThat(token.isValid()).isFalse();
        }
    }

    // ==================== Administrateur ====================

    @Nested
    class AdministrateurTest {
        @Test
        void convenienceConstructor_setsAllFields() {
            Administrateur admin = new Administrateur("CIN123", "Nom", "Prenom", "admin@test.com", "hashed", "IT");
            assertThat(admin.getCin()).isEqualTo("CIN123");
            assertThat(admin.getNom()).isEqualTo("Nom");
            assertThat(admin.getPrenom()).isEqualTo("Prenom");
            assertThat(admin.getEmail()).isEqualTo("admin@test.com");
            assertThat(admin.getMotDePasse()).isEqualTo("hashed");
            assertThat(admin.getRole()).isEqualTo(RoleUtilisateur.ADMIN);
            assertThat(admin.isEnabled()).isTrue();
            assertThat(admin.getDepartement()).isEqualTo("IT");
        }

        @Test
        void noArgsConstructor_createsInstance() {
            Administrateur admin = new Administrateur();
            assertThat(admin).isNotNull();
        }

        @Test
        void departement_getterSetter() {
            Administrateur admin = new Administrateur();
            admin.setDepartement("Finance");
            assertThat(admin.getDepartement()).isEqualTo("Finance");
        }
    }

    // ==================== Observateur ====================

    @Nested
    class ObservateurTest {
        @Test
        void organisme_getterSetter() {
            Observateur obs = new Observateur();
            obs.setOrganisme("Electoral Commission");
            assertThat(obs.getOrganisme()).isEqualTo("Electoral Commission");
        }

        @Test
        void allArgsConstructor() {
            Observateur obs = new Observateur("Commission");
            assertThat(obs.getOrganisme()).isEqualTo("Commission");
        }

        @Test
        void noArgsConstructor_createsInstance() {
            Observateur obs = new Observateur();
            assertThat(obs).isNotNull();
        }
    }
}
