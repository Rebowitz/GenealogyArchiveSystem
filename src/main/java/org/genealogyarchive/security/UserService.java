package org.genealogyarchive.security;

import org.genealogyarchive.access.AccessPolicy;
import org.genealogyarchive.access.Actor;
import org.genealogyarchive.access.Permission;
import org.genealogyarchive.access.Role;
import org.genealogyarchive.data.AppUserRepository;
import org.genealogyarchive.history.ChangeKinds;
import org.genealogyarchive.history.ChangeRecorder;
import org.genealogyarchive.history.FieldChange;
import org.genealogyarchive.history.FieldClass;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.service.ConflictException;
import org.genealogyarchive.service.NotFoundException;
import org.genealogyarchive.service.RuleViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Local user accounts and roles (BUILD-SPEC s.43). All authorization happens here, at the service
 * boundary. Significant account changes are recorded in Change History; passwords and hashes never are.
 */
public final class UserService {

    public static final String OBJECT_TYPE = "app_user";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final AppUserRepository users;
    private final ChangeRecorder recorder;
    private final PasswordHasher hasher;
    private final TransactionTemplate tx;
    private final Clock clock;

    public UserService(AppUserRepository users, ChangeRecorder recorder, PasswordHasher hasher,
                       TransactionTemplate tx, Clock clock) {
        this.users = users;
        this.recorder = recorder;
        this.hasher = hasher;
        this.tx = tx;
        this.clock = clock;
    }

    public AppUser create(Actor actor, String username, String displayName, Role role, String password) {
        AccessPolicy.require(actor, Permission.MANAGE_USERS);
        String name = validUsername(username);
        String display = validDisplayName(displayName);
        if (role == null) {
            throw new RuleViolationException("A role is required.");
        }
        PasswordPolicy.check(name, password);
        String hash = hasher.hash(password);
        return tx.execute(status -> {
            if (users.usernameTaken(name)) {
                throw new RuleViolationException("The username '" + name + "' is already in use.");
            }
            AppUser user = new AppUser(GlobalIds.newId(), name, display, role, true, clock.instant(), clock.instant(), 1);
            try {
                users.insert(user, hash);
            } catch (DuplicateKeyException e) {
                throw new RuleViolationException("The username '" + name + "' is already in use.");
            }
            recorder.record(actor, OBJECT_TYPE, user.userId(), ChangeKinds.CREATED, List.of(
                    FieldChange.set("user_id", FieldClass.IMMUTABLE, user.userId()),
                    FieldChange.set("username", FieldClass.IMMUTABLE, name),
                    FieldChange.set("display_name", FieldClass.CORRECTABLE, display),
                    FieldChange.set("role", FieldClass.CORRECTABLE, role),
                    FieldChange.set("enabled", FieldClass.CORRECTABLE, true)), null);
            return users.findById(user.userId()).orElseThrow();
        });
    }

    /**
     * Corrects display name, role or enabled state. At least one enabled Administrator always remains.
     */
    public AppUser update(Actor actor, UUID userId, long expectedVersion, String displayName, Role role, boolean enabled) {
        AccessPolicy.require(actor, Permission.MANAGE_USERS);
        String display = validDisplayName(displayName);
        if (role == null) {
            throw new RuleViolationException("A role is required.");
        }
        return tx.execute(status -> {
            List<UUID> enabledAdmins = users.lockEnabledAdministrators();
            AppUser current = users.findById(userId).orElseThrow(() -> new NotFoundException("The user"));
            if (current.version() != expectedVersion) {
                throw new ConflictException("The user account");
            }
            boolean losesAdmin = current.enabled() && current.role() == Role.ADMINISTRATOR
                    && (!enabled || role != Role.ADMINISTRATOR);
            if (losesAdmin && enabledAdmins.size() <= 1) {
                throw new RuleViolationException("The archive must keep at least one enabled Administrator.");
            }
            if (!enabled && userId.equals(actor.userId())) {
                throw new RuleViolationException("You cannot disable your own account.");
            }
            List<FieldChange> changes = List.of(
                    FieldChange.of("display_name", FieldClass.CORRECTABLE, current.displayName(), display),
                    FieldChange.of("role", FieldClass.CORRECTABLE, current.role(), role),
                    FieldChange.of("enabled", FieldClass.CORRECTABLE, current.enabled(), enabled));
            if (changes.stream().noneMatch(FieldChange::changed)) {
                return current;
            }
            if (!users.update(userId, expectedVersion, display, role, enabled, clock.instant())) {
                throw new ConflictException("The user account");
            }
            recorder.record(actor, OBJECT_TYPE, userId, ChangeKinds.CORRECTED, changes, null);
            return users.findById(userId).orElseThrow();
        });
    }

    /** Administrator sets a new password for another account. */
    public void resetPassword(Actor actor, UUID userId, long expectedVersion, String newPassword) {
        AccessPolicy.require(actor, Permission.MANAGE_USERS);
        tx.executeWithoutResult(status -> {
            AppUser current = users.findById(userId).orElseThrow(() -> new NotFoundException("The user"));
            PasswordPolicy.check(current.username(), newPassword);
            if (!users.updatePassword(userId, expectedVersion, hasher.hash(newPassword), clock.instant())) {
                throw new ConflictException("The user account");
            }
            recorder.record(actor, OBJECT_TYPE, userId, ChangeKinds.PASSWORD_RESET, List.of(), null);
        });
    }

    /** A signed-in user changes their own password, proving the current one. */
    public void changeOwnPassword(Actor actor, String currentPassword, String newPassword) {
        AccessPolicy.require(actor, Permission.CHANGE_OWN_PASSWORD);
        if (actor.isSystem()) {
            throw new RuleViolationException("Only a signed-in user can change their own password.");
        }
        tx.executeWithoutResult(status -> {
            AppUser current = users.findById(actor.userId()).orElseThrow(() -> new NotFoundException("The user"));
            String hash = users.passwordHash(current.userId()).orElse(null);
            if (!hasher.matches(currentPassword == null ? "" : currentPassword, hash)) {
                throw new RuleViolationException("The current password is not correct.");
            }
            PasswordPolicy.check(current.username(), newPassword);
            if (!users.updatePassword(current.userId(), current.version(), hasher.hash(newPassword), clock.instant())) {
                throw new ConflictException("Your account");
            }
            recorder.record(actor, OBJECT_TYPE, current.userId(), ChangeKinds.PASSWORD_CHANGED, List.of(), null);
        });
    }

    public List<AppUser> list(Actor actor) {
        AccessPolicy.require(actor, Permission.MANAGE_USERS);
        return users.findAll();
    }

    public AppUser get(Actor actor, UUID userId) {
        AccessPolicy.require(actor, Permission.MANAGE_USERS);
        return users.findById(userId).orElseThrow(() -> new NotFoundException("The user"));
    }

    /** Current state of a signed-in user: present only while the account exists and is enabled. */
    public Optional<AppUser> activeUser(UUID userId) {
        return users.findById(userId).filter(AppUser::enabled);
    }

    /** For sign-in only. */
    public Optional<AppUserRepository.Credentials> credentials(String username) {
        return users.findCredentialsByUsername(username == null ? "" : username.strip());
    }

    public long countUsers() {
        return users.countUsers();
    }

    static String validUsername(String username) {
        String u = username == null ? "" : username.strip();
        if (!USERNAME.matcher(u).matches()) {
            throw new RuleViolationException("Usernames are 1 to 64 letters, digits, '.', '_' or '-'.");
        }
        return u;
    }

    static String validDisplayName(String displayName) {
        String d = displayName == null ? "" : displayName.strip();
        if (d.isEmpty() || d.length() > 200) {
            throw new RuleViolationException("The display name must be 1 to 200 characters.");
        }
        return d;
    }
}
