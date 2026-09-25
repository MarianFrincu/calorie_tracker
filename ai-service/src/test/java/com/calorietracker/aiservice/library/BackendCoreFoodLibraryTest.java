package com.calorietracker.aiservice.library;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.calorietracker.aiservice.config.BackendCoreConfig;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class BackendCoreFoodLibraryTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://backend-core")
            .requestInterceptor(BackendCoreConfig.forwardAuthorization());
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final BackendCoreFoodLibrary library = new BackendCoreFoodLibrary(builder.build());

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static String ingredient(String name, int kcal) {
        return "{\"id\":1,\"name\":\"" + name + "\",\"brand\":null,\"kcalPer100g\":" + kcal
                + ",\"proteinPer100g\":1,\"carbsPer100g\":2,\"fatPer100g\":3,\"fiberPer100g\":4,\"isPublic\":true}";
    }

    @Test
    void searchesTheSingularAndForwardsTheCallersToken() {
        MockHttpServletRequest incoming = new MockHttpServletRequest();
        incoming.addHeader("Authorization", "Bearer user-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));

        server.expect(requestTo("http://backend-core/api/ingredients?scope=all&q=egg&size=10"))
                .andExpect(header("Authorization", "Bearer user-token"))
                .andRespond(withSuccess("[" + ingredient("Eggplant", 25) + "," + ingredient("Egg", 155) + "]",
                        MediaType.APPLICATION_JSON));

        FoodLibrary.Food egg = library.find("eggs").orElseThrow();
        assertThat(egg.name()).isEqualTo("Egg"); // exact name beats a longer prefix match
        assertThat(egg.kcalPer100g()).isEqualTo(155);
        server.verify();
    }

    @Test
    void prefersNameStartingWithTheQueryAndThenTheShortest() {
        BackendCoreFoodLibrary.Ingredient best = BackendCoreFoodLibrary.best("rice", List.of(
                new BackendCoreFoodLibrary.Ingredient("Brown rice, cooked", 111, 0, 0, 0, 0),
                new BackendCoreFoodLibrary.Ingredient("Rice cakes, puffed", 387, 0, 0, 0, 0),
                new BackendCoreFoodLibrary.Ingredient("Rice, cooked", 130, 0, 0, 0, 0)));
        assertThat(best.name()).isEqualTo("Rice, cooked");
    }

    @Test
    void triesThePluralWhenTheSingularFindsNothing() {
        server.expect(requestTo("http://backend-core/api/ingredients?scope=all&q=almond&size=10"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://backend-core/api/ingredients?scope=all&q=almonds&size=10"))
                .andRespond(withSuccess("[" + ingredient("Almonds", 579) + "]", MediaType.APPLICATION_JSON));
        assertThat(library.find("almonds")).map(FoodLibrary.Food::name).contains("Almonds");
        server.verify();
    }

    @Test
    void aBareGenericWordMeansTheEverydayFood() {
        server.expect(requestTo("http://backend-core/api/ingredients?scope=all&q=rice&size=10"))
                .andRespond(withSuccess("[" + ingredient("Rice noodles, cooked", 109) + "," + ingredient("White rice, cooked", 130) + "]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://backend-core/api/ingredients?scope=all&q=White%20rice,%20cooked&size=10"))
                .andRespond(withSuccess("[" + ingredient("White rice, cooked", 130) + "]", MediaType.APPLICATION_JSON));
        assertThat(library.find("rice")).map(FoodLibrary.Food::name).contains("White rice, cooked");
        server.verify();
    }

    @Test
    void theUsersOwnExactFoodBeatsTheEverydayDefault() {
        server.expect(requestTo("http://backend-core/api/ingredients?scope=all&q=rice&size=10"))
                .andRespond(withSuccess("[" + ingredient("Rice", 350) + "," + ingredient("White rice, cooked", 130) + "]",
                        MediaType.APPLICATION_JSON));
        assertThat(library.find("rice")).map(FoodLibrary.Food::kcalPer100g).contains(350);
        server.verify();
    }

    @Test
    void anUnreachableOrFailingLibraryMeansNoMatch() {
        server.expect(requestTo("http://backend-core/api/ingredients?scope=all&q=egg&size=10"))
                .andRespond(withServerError());
        assertThat(library.find("egg")).isEmpty();
    }
}
