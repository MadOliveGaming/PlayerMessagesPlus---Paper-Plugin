package com.madolivegaming.playermessagesplus;

import com.google.gson.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class JsonDataStore {
    private final PlayerMessagesPlus plugin;
    private final Path folder;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Map<String, List<String>> shared = new HashMap<>();
    private Map<UUID, PersonalMessages> personal = new HashMap<>();
    private Set<UUID> banned = new HashSet<>();
    private boolean banDataUnreadable;
    private final List<ValidationIssue> latestIssues = new ArrayList<>();

    public JsonDataStore(PlayerMessagesPlus plugin) {
        this.plugin = plugin;
        this.folder = plugin.dataDirectory().toPath();
        shared.put("join", List.of()); shared.put("leave", List.of()); shared.put("rejoin", List.of());
    }

    public record PersonalMessages(String joinMessage, String leaveMessage, String rejoinMessage) {
        public String get(String type) {
            return switch (type) { case "join" -> joinMessage; case "leave" -> leaveMessage; default -> rejoinMessage; };
        }
        public PersonalMessages with(String type, String value) {
            return switch (type) {
                case "join" -> new PersonalMessages(value, leaveMessage, rejoinMessage);
                case "leave" -> new PersonalMessages(joinMessage, value, rejoinMessage);
                default -> new PersonalMessages(joinMessage, leaveMessage, value);
            };
        }
    }

    public List<String> shared(String type) { return shared.getOrDefault(type, List.of()); }
    public String personal(UUID uuid, String type) {
        PersonalMessages p = personal.get(uuid); return p == null ? null : p.get(type);
    }
    public boolean isBanned(UUID uuid) { return banDataUnreadable || banned.contains(uuid); }
    public boolean banDataUnreadable() { return banDataUnreadable; }
    public Set<UUID> bannedPlayers() { return Collections.unmodifiableSet(banned); }
    public List<ValidationIssue> latestIssues() { return List.copyOf(latestIssues); }

    public void initializeFiles() {
        try { Files.createDirectories(folder); }
        catch (IOException e) { plugin.getLogger().severe("Could not create plugin data folder: " + e.getMessage()); }
        createIfMissing("join-messages.json", sharedTemplate("join", "<green><player> joined the game"));
        createIfMissing("leave-messages.json", sharedTemplate("leave", "<red><player> left the game"));
        createIfMissing("rejoin-messages.json", sharedTemplate("rejoin", "<yellow><player> rejoined the game"));
        createIfMissing("player-messages.json", "{\n  \"_comment\": [\"Personal messages are keyed by player UUID.\", \"Each value may contain joinMessage, leaveMessage, and rejoinMessage strings.\"],\n  \"players\": {}\n}\n");
        createIfMissing("banned-players.json", "{\n  \"_comment\": [\"UUIDs listed here cannot use personal join, leave, or rejoin messages.\"],\n  \"bannedPlayers\": []\n}\n");
    }

    private String sharedTemplate(String type, String example) {
        return "{\n  \"_comment\": [\"Supported MiniMessage tags include named/hex colors, decorations, gradients, rainbow, and the <player> placeholder.\", \"Unknown tags and interactive tags such as click/hover are not supported.\"],\n  \"messages\": [\"" + example + "\"]\n}\n";
    }

    private void createIfMissing(String name, String content) {
        Path p = folder.resolve(name);
        if (Files.exists(p)) return;
        try { Files.writeString(p, content, StandardCharsets.UTF_8); }
        catch (IOException e) { plugin.getLogger().severe("Could not create " + name + ": " + e.getMessage()); }
    }

    public List<ValidationIssue> validateAll() {
        List<ValidationIssue> issues = new ArrayList<>();
        for (String type : List.of("join", "leave", "rejoin")) validateSharedFile(type, issues);
        validatePlayerFile(issues); validateBanFile(issues);
        return issues;
    }

    public void refreshIssueCache() {
        latestIssues.clear(); latestIssues.addAll(validateAll());
        long files = latestIssues.stream().map(ValidationIssue::file).distinct().count();
        plugin.setIssueCache(latestIssues.size(), (int) files);
    }

    public List<String> describeValidation() {
        List<ValidationIssue> issues = validateAll();
        latestIssues.clear(); latestIssues.addAll(issues);
        long files = issues.stream().map(ValidationIssue::file).distinct().count();
        plugin.setIssueCache(issues.size(), (int) files);
        if (issues.isEmpty()) return List.of("[PlayerMessages+] Verification complete: no JSON issues found.");
        List<String> out = new ArrayList<>();
        out.add("[PlayerMessages+] Verification found " + issues.size() + " issue(s) in " + files + " JSON file(s):");
        for (ValidationIssue i : issues) out.add("- " + i);
        return out;
    }

    public boolean hasSharedFileIssues(String type) {
        String file = type + "-messages.json";
        return latestIssues.stream().anyMatch(i -> i.file().equals(file));
    }

    private JsonElement readJson(String file, List<ValidationIssue> issues) {
        Path p = folder.resolve(file);
        if (!Files.exists(p)) { issues.add(new ValidationIssue(file, "", "file is missing")); return null; }
        try { return JsonParser.parseString(Files.readString(p, StandardCharsets.UTF_8)); }
        catch (Exception e) { issues.add(new ValidationIssue(file, "", "malformed or unreadable JSON")); return null; }
    }

    private void validateSharedFile(String type, List<ValidationIssue> issues) {
        String file = type + "-messages.json";
        JsonElement root = readJson(file, issues);
        if (root == null || !root.isJsonObject()) { if (root != null) issues.add(new ValidationIssue(file, "", "root must be an object")); return; }
        validateComment(file, root.getAsJsonObject(), issues);
        JsonElement messages = root.getAsJsonObject().get("messages");
        if (messages == null || !messages.isJsonArray()) { issues.add(new ValidationIssue(file, "messages", "required field must be an array")); return; }
        JsonArray a = messages.getAsJsonArray();
        for (int i = 0; i < a.size(); i++) {
            JsonElement e = a.get(i);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString() || e.getAsString().isBlank())
                issues.add(new ValidationIssue(file, "messages[" + i + "]", "entry must be a non-empty string"));
            else if (!MiniMessageService.validateShared(e.getAsString()).valid())
                issues.add(new ValidationIssue(file, "messages[" + i + "]", "contains invalid or unsupported MiniMessage tags"));
        }
    }

    private void validatePlayerFile(List<ValidationIssue> issues) {
        String file = "player-messages.json";
        JsonElement root = readJson(file, issues);
        if (root == null || !root.isJsonObject()) { if (root != null) issues.add(new ValidationIssue(file, "", "root must be an object")); return; }
        validateComment(file, root.getAsJsonObject(), issues);
        JsonElement players = root.getAsJsonObject().get("players");
        if (players == null || !players.isJsonObject()) { issues.add(new ValidationIssue(file, "players", "required field must be an object")); return; }
        for (Map.Entry<String, JsonElement> entry : players.getAsJsonObject().entrySet()) {
            try { UUID.fromString(entry.getKey()); } catch (Exception ex) { issues.add(new ValidationIssue(file, entry.getKey(), "invalid player UUID")); continue; }
            if (!entry.getValue().isJsonObject()) { issues.add(new ValidationIssue(file, entry.getKey(), "player record must be an object")); continue; }
            for (String key : List.of("joinMessage", "leaveMessage", "rejoinMessage")) {
                JsonElement v = entry.getValue().getAsJsonObject().get(key);
                if (v == null) continue;
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString()) issues.add(new ValidationIssue(file, entry.getKey() + "." + key, "message must be a string"));
                else if (v.getAsString().isBlank()) issues.add(new ValidationIssue(file, entry.getKey() + "." + key, "message must not be empty"));
                else if (v.getAsString().length() > 256) issues.add(new ValidationIssue(file, entry.getKey() + "." + key, "message exceeds 256 characters"));
                else {
                    MiniMessageService.Validation result = MiniMessageService.validatePersonal(v.getAsString(), new PluginSettings());
                    if (!result.valid()) issues.add(new ValidationIssue(file, entry.getKey() + "." + key, "message contains invalid or unsupported MiniMessage tags"));
                }
            }
        }
    }

    private void validateBanFile(List<ValidationIssue> issues) {
        String file = "banned-players.json";
        JsonElement root = readJson(file, issues);
        if (root == null || !root.isJsonObject()) { if (root != null) issues.add(new ValidationIssue(file, "", "root must be an object")); return; }
        validateComment(file, root.getAsJsonObject(), issues);
        JsonElement bans = root.getAsJsonObject().get("bannedPlayers");
        if (bans == null || !bans.isJsonArray()) { issues.add(new ValidationIssue(file, "bannedPlayers", "required field must be an array")); return; }
        for (int i = 0; i < bans.getAsJsonArray().size(); i++) {
            JsonElement e = bans.getAsJsonArray().get(i);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) issues.add(new ValidationIssue(file, "bannedPlayers[" + i + "]", "entry must be a UUID string"));
            else try { UUID.fromString(e.getAsString()); } catch (Exception ex) { issues.add(new ValidationIssue(file, "bannedPlayers[" + i + "]", "invalid UUID")); }
        }
    }

    private void validateComment(String file, JsonObject root, List<ValidationIssue> issues) {
        JsonElement comment = root.get("_comment");
        if (comment == null || !comment.isJsonArray()) {
            issues.add(new ValidationIssue(file, "_comment", "required field must be an array of strings"));
            return;
        }
        JsonArray array = comment.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement item = array.get(i);
            if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString())
                issues.add(new ValidationIssue(file, "_comment[" + i + "]", "entry must be a string"));
        }
    }

    public void loadAll(boolean reload) {
        List<ValidationIssue> parseIssues = new ArrayList<>();
        for (String type : List.of("join", "leave", "rejoin")) {
            String file = type + "-messages.json";
            List<ValidationIssue> local = new ArrayList<>();
            JsonElement root = readJson(file, local);
            if (root == null || !root.isJsonObject()) {
                logIssues(local, "Could not load " + file); parseIssues.addAll(local); continue;
            }
            JsonElement messages = root.getAsJsonObject().get("messages");
            if (messages == null || !messages.isJsonArray()) {
                local.add(new ValidationIssue(file, "messages", "required field must be an array")); logIssues(local, "Could not load " + file); parseIssues.addAll(local); continue;
            }
            List<String> valid = new ArrayList<>();
            JsonArray a = messages.getAsJsonArray();
            for (int i = 0; i < a.size(); i++) {
                JsonElement e = a.get(i);
                if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && !e.getAsString().isBlank()) {
                    MiniMessageService.Validation v = MiniMessageService.validateShared(e.getAsString());
                    if (v.valid()) valid.add(e.getAsString()); else local.add(new ValidationIssue(file, "messages[" + i + "]", "invalid or unsupported MiniMessage tags; skipped"));
                } else local.add(new ValidationIssue(file, "messages[" + i + "]", "entry must be a non-empty string; skipped"));
            }
            shared.put(type, List.copyOf(valid));
            if (!local.isEmpty()) logIssues(local, "Issues while loading " + file);
            parseIssues.addAll(local);
        }
        loadPlayerData(parseIssues);
        loadBanData(parseIssues);
        if (!parseIssues.isEmpty()) plugin.getLogger().warning("Some data entries were skipped. Run /pmp verify for a full report.");
    }

    private void loadPlayerData(List<ValidationIssue> issues) {
        String file = "player-messages.json";
        List<ValidationIssue> local = new ArrayList<>();
        JsonElement root = readJson(file, local);
        if (root == null || !root.isJsonObject()) { personal = new HashMap<>(); logIssues(local, "Player message data unavailable; personal messages are disabled until the file is repaired"); issues.addAll(local); return; }
        JsonElement players = root.getAsJsonObject().get("players");
        if (players == null || !players.isJsonObject()) { local.add(new ValidationIssue(file, "players", "required field must be an object")); personal = new HashMap<>(); logIssues(local, "Player message data unavailable; personal messages are disabled until the file is repaired"); issues.addAll(local); return; }
        Map<UUID, PersonalMessages> loaded = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : players.getAsJsonObject().entrySet()) {
            UUID uuid;
            try { uuid = UUID.fromString(e.getKey()); } catch (Exception ex) { local.add(new ValidationIssue(file, e.getKey(), "invalid UUID; skipped")); continue; }
            if (!e.getValue().isJsonObject()) { local.add(new ValidationIssue(file, e.getKey(), "record is not an object; skipped")); continue; }
            JsonObject o = e.getValue().getAsJsonObject();
            String[] values = new String[3]; String[] keys = {"joinMessage", "leaveMessage", "rejoinMessage"};
            for (int i = 0; i < keys.length; i++) {
                JsonElement v = o.get(keys[i]);
                if (v == null) continue;
                if (v.isJsonNull()) { local.add(new ValidationIssue(file, e.getKey() + "." + keys[i], "message is null; skipped")); continue; }
                if (!v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString() || v.getAsString().isBlank() || v.getAsString().length() > 256) { local.add(new ValidationIssue(file, e.getKey() + "." + keys[i], "unreadable message; skipped")); continue; }
                MiniMessageService.Validation val = MiniMessageService.validatePersonal(v.getAsString(), new PluginSettings());
                if (!val.valid()) { local.add(new ValidationIssue(file, e.getKey() + "." + keys[i], "invalid message; skipped")); continue; }
                values[i] = v.getAsString();
            }
            loaded.put(uuid, new PersonalMessages(values[0], values[1], values[2]));
        }
        personal = loaded;
        logIssues(local, "Issues in " + file); issues.addAll(local);
    }

    private void loadBanData(List<ValidationIssue> issues) {
        String file = "banned-players.json";
        List<ValidationIssue> local = new ArrayList<>();
        JsonElement root = readJson(file, local);
        if (root == null || !root.isJsonObject()) { banDataUnreadable = true; logIssues(local, "Ban data unreadable; personal messages are disabled for non-operators until repaired"); issues.addAll(local); return; }
        JsonElement bans = root.getAsJsonObject().get("bannedPlayers");
        if (bans == null || !bans.isJsonArray()) { local.add(new ValidationIssue(file, "bannedPlayers", "required field must be an array")); banDataUnreadable = true; logIssues(local, "Ban data unreadable; personal messages are disabled for non-operators until repaired"); issues.addAll(local); return; }
        Set<UUID> loaded = new HashSet<>();
        for (int i = 0; i < bans.getAsJsonArray().size(); i++) {
            JsonElement e = bans.getAsJsonArray().get(i);
            if (!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) { local.add(new ValidationIssue(file, "bannedPlayers[" + i + "]", "invalid entry; skipped")); continue; }
            try { loaded.add(UUID.fromString(e.getAsString())); } catch (Exception ex) { local.add(new ValidationIssue(file, "bannedPlayers[" + i + "]", "invalid UUID; skipped")); }
        }
        banned = loaded;
        banDataUnreadable = !local.isEmpty();
        if (banDataUnreadable) logIssues(local, "Issues in " + file + "; personal messages are disabled for non-operators until repaired");
        issues.addAll(local);
    }

    private void logIssues(List<ValidationIssue> issues, String heading) {
        if (issues.isEmpty()) return;
        plugin.getLogger().warning(heading + ":");
        for (ValidationIssue issue : issues) plugin.getLogger().warning(" - " + issue);
    }

    public boolean setPersonal(UUID uuid, String type, String message) {
        List<ValidationIssue> currentIssues = new ArrayList<>();
        validatePlayerFile(currentIssues);
        if (!currentIssues.isEmpty()) { plugin.getLogger().warning("Refusing to modify damaged player-messages.json; repair or back it up first."); return false; }
        Map<UUID, PersonalMessages> next = new HashMap<>(personal);
        PersonalMessages current = next.getOrDefault(uuid, new PersonalMessages(null, null, null));
        next.put(uuid, current.with(type, message));
        if (next.get(uuid).joinMessage() == null && next.get(uuid).leaveMessage() == null && next.get(uuid).rejoinMessage() == null) next.remove(uuid);
        JsonObject root = readObjectForWrite("player-messages.json", "players");
        if (root == null) return false;
        JsonObject players = root.getAsJsonObject("players");
        JsonObject o = players.has(uuid.toString()) && players.get(uuid.toString()).isJsonObject() ? players.getAsJsonObject(uuid.toString()) : new JsonObject();
        String key = type + "Message";
        if (message == null) o.remove(key); else o.addProperty(key, message);
        if (o.size() == 0) players.remove(uuid.toString()); else players.add(uuid.toString(), o);
        if (!writeJson("player-messages.json", root)) return false;
        personal = next; return true;
    }

    public boolean setBanned(UUID uuid, boolean value) {
        List<ValidationIssue> currentIssues = new ArrayList<>();
        validateBanFile(currentIssues);
        if (!currentIssues.isEmpty()) { plugin.getLogger().warning("Refusing to modify damaged banned-players.json; repair or back it up first."); return false; }
        Set<UUID> next = new HashSet<>(banned);
        if (value) next.add(uuid); else next.remove(uuid);
        JsonObject root = readObjectForWrite("banned-players.json", "bannedPlayers");
        if (root == null) return false;
        JsonArray a = new JsonArray(); next.stream().map(UUID::toString).sorted().forEach(a::add);
        root.add("bannedPlayers", a);
        if (!writeJson("banned-players.json", root)) return false;
        banned = next; banDataUnreadable = false; return true;
    }

    private JsonObject readObjectForWrite(String file, String child) {
        try {
            JsonElement e = JsonParser.parseString(Files.readString(folder.resolve(file), StandardCharsets.UTF_8));
            if (!e.isJsonObject() || !e.getAsJsonObject().has(child) || !e.getAsJsonObject().get(child).isJsonObject() && !e.getAsJsonObject().get(child).isJsonArray()) return null;
            return e.getAsJsonObject();
        } catch (Exception e) { plugin.getLogger().warning("Refusing to overwrite damaged " + file + ". Repair or back it up first."); return null; }
    }

    private boolean writeJson(String file, JsonObject root) {
        Path target = folder.resolve(file); Path temp = folder.resolve(file + ".tmp");
        try {
            Files.writeString(temp, gson.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);
            try { Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (IOException e) { Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            return true;
        } catch (IOException e) { plugin.getLogger().severe("Could not save " + file + ": " + e.getMessage()); try { Files.deleteIfExists(temp); } catch (IOException ignored) {} return false; }
    }
}
