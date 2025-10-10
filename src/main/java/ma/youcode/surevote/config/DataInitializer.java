package ma.youcode.surevote.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.youcode.surevote.domain.entity.*;
import ma.youcode.surevote.domain.enums.RoleUtilisateur;
import ma.youcode.surevote.domain.enums.StatutElection;
import ma.youcode.surevote.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Development data initializer for SUREVOTE.
 *
 * Runs once at application startup (via CommandLineRunner) when the
 * utilisateurs table is empty. Idempotent — will not re-seed if data already exists.
 *
 * Seeded dataset:
 *   - 1  Administrator account
 *   - 1  Observer account
 *   - 2  Electoral colleges (Informatique, Commerce)
 *   - 10 Voter accounts (5 per college, 2FA disabled for dev convenience)
 *   - 3  Elections:
 *        • Currently OUVERTE     — open to all voters (demo voting)
 *        • BROUILLON             — restricted to Informatique college
 *        • PUBLIEE               — historical election with published results
 *   - 8  Candidates across the three elections
 *
 * Default password for every seeded account: {@value #DEFAULT_PASSWORD}
 *
 * IMPORTANT: This bean is excluded from the "prod" Spring profile.
 *            Never activate in production!
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Profile({"default", "dev"})
public class DataInitializer implements CommandLineRunner {

    private final UtilisateurRepository       utilisateurRepository;
    private final ElectionRepository          electionRepository;
    private final CandidatRepository          candidatRepository;
    private final CollegeElectoralRepository  collegeElectoralRepository;
    private final PasswordEncoder             passwordEncoder;

    /** Plaintext password used for every seeded account. */
    static final String DEFAULT_PASSWORD = "Password123!";

    // =========================================================
    // Entry point
    // =========================================================

    @Override
    @Transactional
    public void run(String... args) {
        if (utilisateurRepository.count() > 0) {
            log.info("[DataInitializer] Database already seeded — skipping.");
            return;
        }

        log.info("[DataInitializer] ═══════════════════════════════════════");
        log.info("[DataInitializer]  Seeding development data …            ");
        log.info("[DataInitializer] ═══════════════════════════════════════");

        String hash = passwordEncoder.encode(DEFAULT_PASSWORD);

        // ── Users ────────────────────────────────────────────────────────────
        createAdmin(hash);
        createObserver(hash);

        // ── Electoral colleges ───────────────────────────────────────────────
        CollegeElectoral collegeInfo = createCollege(
                "Département Informatique",
                "Collège électoral des étudiants inscrits dans la filière Développement Informatique."
        );
        CollegeElectoral collegeCommerce = createCollege(
                "Département Commerce & Gestion",
                "Collège électoral des étudiants inscrits dans la filière Commerce et Management."
        );

        // ── Voters ───────────────────────────────────────────────────────────
        createVoters(hash, collegeInfo, new String[][] {
            // CIN        Prénom      Nom              Email                           Téléphone
            { "BC100001", "Ahmed",    "Benali",        "ahmed.benali@youcode.ma",      "+212601000001" },
            { "BC100002", "Sara",     "El Fassi",      "sara.elfassi@youcode.ma",      "+212601000002" },
            { "BC100003", "Karim",    "Idrissi",       "karim.idrissi@youcode.ma",     "+212601000003" },
            { "BC100004", "Fatima",   "Zahra",         "fatima.zahra@youcode.ma",      "+212601000004" },
            { "BC100005", "Youssef",  "Mansouri",      "youssef.mansouri@youcode.ma",  "+212601000005" },
        });

        createVoters(hash, collegeCommerce, new String[][] {
            { "BC200001", "Nadia",    "Tazi",          "nadia.tazi@youcode.ma",        "+212602000001" },
            { "BC200002", "Omar",     "Berrada",       "omar.berrada@youcode.ma",      "+212602000002" },
            { "BC200003", "Layla",    "Cherkaoui",     "layla.cherkaoui@youcode.ma",   "+212602000003" },
            { "BC200004", "Hamza",    "Tahiri",        "hamza.tahiri@youcode.ma",      "+212602000004" },
            { "BC200005", "Imane",    "Bensouda",      "imane.bensouda@youcode.ma",    "+212602000005" },
        });

        // ── Elections ────────────────────────────────────────────────────────
        LocalDateTime now = LocalDateTime.now();

        // ELECTION 1: OUVERTE — open to all voters right now (great for live demo)
        Election openElection = createElection(
                "Élection des délégués étudiants — Promo 2025",
                "Élection des représentants étudiants pour l'année académique 2025. "
                + "Les délégués élus siégeront au conseil pédagogique et représenteront "
                + "l'ensemble des étudiants auprès de la direction.",
                now.minusHours(1),
                now.plusHours(23),
                StatutElection.OUVERTE,
                null   // no college restriction — all voters eligible
        );
        addCandidats(openElection, new Object[][] {
            // Prénom     Nom           Affiliation                           Biographie
            { "Rida",    "Alami",    "Liste Indépendante Progressiste",
              "Étudiant en 3ème année, spécialité DevOps. Engagé pour l'amélioration "
              + "des ressources pédagogiques numériques et l'accès aux laboratoires." },
            { "Sophia",  "Rahimi",   "Collectif Étudiant Uni",
              "Étudiante en 2ème année, mention Data Science. Candidate pour renforcer "
              + "l'entraide inter-promos et l'accompagnement des nouveaux arrivants." },
            { "Mehdi",   "Ouazzani", "Mouvement Pour L'Excellence",
              "Étudiant en 3ème année, spécialité Cloud & DevSecOps. Propose un plan "
              + "concret pour l'employabilité et les partenariats avec les entreprises locales." },
        });

        // ELECTION 2: BROUILLON — restricted to Informatique college (needs planning)
        Election draftElection = createElection(
                "Élection du bureau de l'association IA — 2025",
                "Élection interne du bureau de l'association Intelligence Artificielle de YouCode. "
                + "Réservée aux membres du département Informatique. "
                + "Le bureau élu organisera les événements tech pour l'année 2025.",
                now.plusDays(7),
                now.plusDays(8),
                StatutElection.BROUILLON,
                collegeInfo
        );
        addCandidats(draftElection, new Object[][] {
            { "Anas",    "El Khayat", "Liste Technique & Innovation",
              "Passionné de ML et robotique. Propose de lancer des projets collaboratifs "
              + "avec des startups et des entreprises de la région." },
            { "Rim",     "Benomar",   "Initiative Ouverte",
              "Active dans plusieurs hackathons nationaux. Veut ouvrir l'association "
              + "aux événements inter-écoles et aux conférences internationales." },
            { "Zakaria", "Filali",    "Projet Solidaire",
              "Organisateur de sessions de formation gratuites en Python et en IA. "
              + "Axe sa candidature sur la montée en compétences et la solidarité entre étudiants." },
        });

        // ELECTION 3: PUBLIEE — historical, published results visible on public dashboard
        Election publishedElection = createElection(
                "Élection du conseil de classe — Filière Web 2024",
                "Résultats officiels de l'élection du conseil de classe de la filière "
                + "Développement Web Full-Stack, session décembre 2024. "
                + "Scrutin à un tour, suffrage direct à bulletin secret.",
                now.minusDays(30),
                now.minusDays(29),
                StatutElection.PUBLIEE,
                null
        );
        addCandidats(publishedElection, new Object[][] {
            { "Khalid",  "Benmoussa", "Union Web Dev",
              "Ancien délégué très apprécié pour son organisation de sessions de code review "
              + "et sa communication transparente avec l'administration." },
            { "Hajar",   "Znati",     "Collectif Inclusif",
              "Militante pour l'égalité des chances dans les formations numériques "
              + "et la diversité au sein des équipes de développement." },
        });

        // ── Summary ──────────────────────────────────────────────────────────
        log.info("[DataInitializer] ═══════════════════════════════════════");
        log.info("[DataInitializer]  Seed complete!                        ");
        log.info("[DataInitializer] ───────────────────────────────────────");
        log.info("[DataInitializer]  Accounts (password: '{}'):", DEFAULT_PASSWORD);
        log.info("[DataInitializer]    ADMIN      → admin@surevote.ma");
        log.info("[DataInitializer]    OBSERVATEUR→ observateur@surevote.ma");
        log.info("[DataInitializer]    ELECTEUR   → ahmed.benali@youcode.ma");
        log.info("[DataInitializer]                 (+ 9 more voters)");
        log.info("[DataInitializer] ───────────────────────────────────────");
        log.info("[DataInitializer]  Elections:");
        log.info("[DataInitializer]    OUVERTE    → id=1 (valid for ~23h)");
        log.info("[DataInitializer]    BROUILLON  → id=2 (starts in 7 days)");
        log.info("[DataInitializer]    PUBLIEE    → id=3 (historical results)");
        log.info("[DataInitializer] ───────────────────────────────────────");
        log.info("[DataInitializer]  Swagger UI → http://localhost:8080/swagger-ui.html");
        log.info("[DataInitializer] ═══════════════════════════════════════");
    }

    // =========================================================
    // Private builder helpers
    // =========================================================

    private Administrateur createAdmin(String hash) {
        Administrateur admin = new Administrateur();
        admin.setCin("AA000001");
        admin.setNom("Admin");
        admin.setPrenom("SUREVOTE");
        admin.setEmail("admin@surevote.ma");
        admin.setMotDePasse(hash);
        admin.setRole(RoleUtilisateur.ADMIN);
        admin.setEnabled(true);
        admin.setDepartement("Direction des Systèmes d'Information");
        return utilisateurRepository.save(admin);
    }

    private Observateur createObserver(String hash) {
        Observateur obs = new Observateur();
        obs.setCin("OB000001");
        obs.setNom("Observateur");
        obs.setPrenom("Officiel");
        obs.setEmail("observateur@surevote.ma");
        obs.setMotDePasse(hash);
        obs.setRole(RoleUtilisateur.OBSERVATEUR);
        obs.setEnabled(true);
        obs.setOrganisme("Commission Nationale de Supervision Électorale");
        return utilisateurRepository.save(obs);
    }

    private CollegeElectoral createCollege(String nom, String description) {
        CollegeElectoral college = new CollegeElectoral();
        college.setNom(nom);
        college.setDescription(description);
        return collegeElectoralRepository.save(college);
    }

    private void createVoters(String hash, CollegeElectoral college, String[][] data) {
        for (String[] row : data) {
            // row: { CIN, Prénom, Nom, Email, Téléphone }
            Electeur electeur = new Electeur();
            electeur.setCin(row[0]);
            electeur.setPrenom(row[1]);
            electeur.setNom(row[2]);
            electeur.setEmail(row[3]);
            electeur.setMotDePasse(hash);
            electeur.setRole(RoleUtilisateur.ELECTEUR);
            electeur.setEnabled(true);
            electeur.setTelephone(row[4]);
            electeur.setDoubleFacteurActif(false); // disabled for dev convenience
            electeur.setOtpVerified(true);
            electeur.setCollegeElectoral(college);
            utilisateurRepository.save(electeur);
        }
        log.info("[DataInitializer] Created {} voters for college '{}'", data.length, college.getNom());
    }

    private Election createElection(String titre,
                                     String description,
                                     LocalDateTime dateDebut,
                                     LocalDateTime dateFin,
                                     StatutElection statut,
                                     CollegeElectoral college) {
        Election election = Election.builder()
                .titre(titre)
                .description(description)
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .statut(statut)
                .collegeElectoral(college)
                .build();
        Election saved = electionRepository.save(election);
        log.info("[DataInitializer] Created election '{}' [{}]", titre, statut);
        return saved;
    }

    private void addCandidats(Election election, Object[][] data) {
        // data rows: { Prénom, Nom, Affiliation, Biographie }
        for (Object[] row : data) {
            Candidat candidat = Candidat.builder()
                    .prenom((String) row[0])
                    .nom((String) row[1])
                    .affiliationOuParti((String) row[2])
                    .biographie((String) row[3])
                    .election(election)
                    .build();
            candidatRepository.save(candidat);
        }
        log.info("[DataInitializer] Added {} candidates to election '{}'",
                data.length, election.getTitre());
    }
}
