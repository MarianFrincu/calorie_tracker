package com.calorietracker.desktop.api;

import com.calorietracker.desktop.model.AddDiaryRequest;
import com.calorietracker.desktop.model.AddWaterRequest;
import com.calorietracker.desktop.model.AddWeightRequest;
import com.calorietracker.desktop.model.CreateIngredientRequest;
import com.calorietracker.desktop.model.CreateRecipeRequest;
import com.calorietracker.desktop.model.DailyNutritionPoint;
import com.calorietracker.desktop.model.DailyWaterPoint;
import com.calorietracker.desktop.model.DaySummary;
import com.calorietracker.desktop.model.DiaryEntry;
import com.calorietracker.desktop.model.Ingredient;
import com.calorietracker.desktop.model.Meal;
import com.calorietracker.desktop.model.MoveOrCopyDiaryRequest;
import com.calorietracker.desktop.model.Objective;
import com.calorietracker.desktop.model.ParseResult;
import com.calorietracker.desktop.model.ParsedRecipe;
import com.calorietracker.desktop.model.Profile;
import com.calorietracker.desktop.model.Recipe;
import com.calorietracker.desktop.model.UpdateObjectiveRequest;
import com.calorietracker.desktop.model.UpdateProfileRequest;
import com.calorietracker.desktop.model.WeightEntry;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * REST client for the Calorie Tracker backend. Always talks to the gateway.
 *
 * <p>Transport security comes from {@link SecureHttp} (TLS 1.2+, verified
 * certificates, connect timeout). In Cognito mode the constructor refuses a
 * plain-http base URL unless it points at this machine, every request carries
 * the current access token, and a 401 triggers one silent token refresh and
 * retry before the session is declared over.
 *
 * Search methods are paginated (page + size). {@code listMy*} returns only the
 * user's own items; {@code searchAll*} additionally includes the public library.
 */
public class ApiClient {

    /** Ordinary calls; generous, but a hung server can no longer freeze a view forever. */
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    /** AI calls wait on a model provider. */
    static final Duration AI_TIMEOUT = Duration.ofSeconds(90);

    private final String baseUrl;
    private final HttpClient http;
    private final AuthSession session;
    private final String timeZone = ZoneId.systemDefault().getId();
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    private volatile Runnable onSessionExpired = () -> {};

    /**
     * @param session the signed-in Cognito session, or null when the backend runs without auth (dev profile)
     */
    public ApiClient(String baseUrl, AuthSession session) {
        SecureHttp.requireSecureTransport(baseUrl, session != null);
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.session = session;
        this.http = SecureHttp.newClient();
    }

    /** Called (on a background thread) when the session can't be refreshed any more. */
    public void setOnSessionExpired(Runnable callback) {
        this.onSessionExpired = callback == null ? () -> {} : callback;
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                // Lets the server decide what "today" means for this user.
                .header("X-Time-Zone", timeZone);
    }

    private String exchange(HttpRequest.Builder builder) throws Exception {
        String token = session == null ? null : session.accessToken();
        HttpResponse<String> resp = send(builder, token);
        if (resp.statusCode() == 401 && session != null) {
            // Expired or revoked access token: refresh once and retry.
            if (session.refresh(token)) {
                resp = send(builder, session.accessToken());
            }
            if (resp.statusCode() == 401) {
                session.clearLocal();
                onSessionExpired.run();
            }
        }
        if (resp.statusCode() / 100 != 2) {
            throw new ApiException(resp.statusCode(), resp.body());
        }
        return resp.body();
    }

    private HttpResponse<String> send(HttpRequest.Builder builder, String token) throws Exception {
        if (token != null) builder.setHeader("Authorization", "Bearer " + token);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private <T> T getJson(String path, Class<T> type) throws Exception {
        return mapper.readValue(exchange(request(path).GET()), type);
    }

    private <T> List<T> getList(String path, Class<T> elementType) throws Exception {
        String body = exchange(request(path).GET());
        return mapper.readValue(body, mapper.getTypeFactory().constructCollectionType(List.class, elementType));
    }

    private <T> T postJson(String path, Object body, Class<T> type) throws Exception {
        return postJson(path, body, type, REQUEST_TIMEOUT);
    }

    private <T> T postJson(String path, Object body, Class<T> type, Duration timeout) throws Exception {
        String json = mapper.writeValueAsString(body);
        String resp = exchange(request(path)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)));
        return type == Void.class || resp.isBlank() ? null : mapper.readValue(resp, type);
    }

    private <T> T putJson(String path, Object body, Class<T> type) throws Exception {
        String json = mapper.writeValueAsString(body);
        String resp = exchange(request(path)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json)));
        return type == Void.class || resp.isBlank() ? null : mapper.readValue(resp, type);
    }

    private void delete(String path) throws Exception {
        exchange(request(path).DELETE());
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String paged(String basePath, String q, int page, int size, String scope) {
        return basePath + "?q=" + enc(q) + "&page=" + page + "&size=" + size + "&scope=" + scope;
    }

    // ---------------- profile ----------------
    public Profile getProfile() throws Exception { return getJson("/api/profile", Profile.class); }
    public Profile updateProfile(UpdateProfileRequest req) throws Exception {
        return putJson("/api/profile", req, Profile.class);
    }
    /** Deletes the account's data on the server. Irreversible. */
    public void deleteProfile() throws Exception { delete("/api/profile"); }

    // ---------------- objective ----------------
    public Objective getObjective() throws Exception { return getJson("/api/objective", Objective.class); }
    public Objective updateObjective(UpdateObjectiveRequest req) throws Exception {
        return putJson("/api/objective", req, Objective.class);
    }

    // ---------------- weight ----------------
    public List<WeightEntry> listWeight() throws Exception {
        return getList("/api/weight", WeightEntry.class);
    }
    public WeightEntry upsertWeight(LocalDate date, double weightKg) throws Exception {
        return postJson("/api/weight", new AddWeightRequest(date.toString(), weightKg), WeightEntry.class);
    }
    public void deleteWeight(long id) throws Exception { delete("/api/weight/" + id); }

    // ---------------- reports ----------------
    public List<DailyNutritionPoint> nutritionReport(LocalDate from, LocalDate to) throws Exception {
        return getList("/api/reports/nutrition?from=" + from + "&to=" + to, DailyNutritionPoint.class);
    }
    public List<DailyWaterPoint> waterReport(LocalDate from, LocalDate to) throws Exception {
        return getList("/api/reports/water?from=" + from + "&to=" + to, DailyWaterPoint.class);
    }

    // ---------------- ingredients ----------------
    /** Only the user's own ingredients (My Library view). */
    public List<Ingredient> listMyIngredients(String q, int page, int size) throws Exception {
        return getList(paged("/api/ingredients", q, page, size, "mine"), Ingredient.class);
    }
    /** User-owned + public library (use when picking ingredients to add to diary or a recipe). */
    public List<Ingredient> searchAllIngredients(String q, int page, int size) throws Exception {
        return getList(paged("/api/ingredients", q, page, size, "all"), Ingredient.class);
    }
    public Ingredient getIngredient(long id) throws Exception {
        return getJson("/api/ingredients/" + id, Ingredient.class);
    }
    public Ingredient createIngredient(CreateIngredientRequest req) throws Exception {
        return postJson("/api/ingredients", req, Ingredient.class);
    }
    public Ingredient updateIngredient(long id, CreateIngredientRequest req) throws Exception {
        return putJson("/api/ingredients/" + id, req, Ingredient.class);
    }
    public void deleteIngredient(long id) throws Exception { delete("/api/ingredients/" + id); }

    // ---------------- recipes ----------------
    /** Only the user's own recipes (My Library view). */
    public List<Recipe> listMyRecipes(String q, int page, int size) throws Exception {
        return getList(paged("/api/recipes", q, page, size, "mine"), Recipe.class);
    }
    /** User-owned + public library (use when picking a recipe to add to the diary). */
    public List<Recipe> searchAllRecipes(String q, int page, int size) throws Exception {
        return getList(paged("/api/recipes", q, page, size, "all"), Recipe.class);
    }
    public Recipe createRecipe(CreateRecipeRequest req) throws Exception {
        return postJson("/api/recipes", req, Recipe.class);
    }
    public Recipe updateRecipe(long id, CreateRecipeRequest req) throws Exception {
        return putJson("/api/recipes/" + id, req, Recipe.class);
    }
    public void deleteRecipe(long id) throws Exception { delete("/api/recipes/" + id); }

    // ---------------- diary ----------------
    public DiaryEntry addDiary(AddDiaryRequest req) throws Exception {
        return postJson("/api/diary", req, DiaryEntry.class);
    }
    public void deleteDiary(long id) throws Exception { delete("/api/diary/" + id); }
    public DiaryEntry moveDiary(long id, LocalDate date, Meal meal) throws Exception {
        return postJson("/api/diary/" + id + "/move",
                new MoveOrCopyDiaryRequest(date.toString(), meal), DiaryEntry.class);
    }
    public DiaryEntry copyDiary(long id, LocalDate date, Meal meal) throws Exception {
        return postJson("/api/diary/" + id + "/copy",
                new MoveOrCopyDiaryRequest(date.toString(), meal), DiaryEntry.class);
    }

    // ---------------- water ----------------
    public void addWater(LocalDate date, int ml) throws Exception {
        postJson("/api/water", new AddWaterRequest(date.toString(), ml), Void.class);
    }
    public void deleteWater(long id) throws Exception { delete("/api/water/" + id); }

    // ---------------- summary (the day view payload) ----------------
    public DaySummary getDaySummary(LocalDate date) throws Exception {
        return getJson("/api/summary?date=" + date, DaySummary.class);
    }

    // ---------------- AI ----------------
    public ParseResult parseIngredients(String text) throws Exception {
        return postJson("/api/ai/parse", Map.of("text", text), ParseResult.class, AI_TIMEOUT);
    }
    public ParsedRecipe parseRecipe(String text) throws Exception {
        return postJson("/api/ai/parse-recipe", Map.of("text", text), ParsedRecipe.class, AI_TIMEOUT);
    }
    public Recipe saveAiRecipe(ParsedRecipe blueprint) throws Exception {
        // Persisting an AI blueprint is a DB write, so it lives in backend-core,
        // not in the stateless ai-service. The gateway routes /api/recipes/** there.
        return postJson("/api/recipes/from-ai", blueprint, Recipe.class);
    }
}
