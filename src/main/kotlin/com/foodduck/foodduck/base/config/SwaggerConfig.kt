package com.foodduck.foodduck.base.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import springfox.documentation.builders.ApiInfoBuilder
import springfox.documentation.builders.PathSelectors
import springfox.documentation.builders.RequestHandlerSelectors
import springfox.documentation.builders.RequestParameterBuilder
import springfox.documentation.service.*
import springfox.documentation.spi.DocumentationType
import springfox.documentation.spi.service.contexts.SecurityContext
import springfox.documentation.spring.web.plugins.Docket
import springfox.documentation.swagger2.annotations.EnableSwagger2

@Configuration
@EnableSwagger2
class SwaggerConfig {

    private fun apiInfo(): ApiInfo? {
        return ApiInfoBuilder()
            .title("Food Duck Api 문서")
            .description("문서 제공")
            .version("1.0")
            .build()
    }

    private fun consumeContentTypes(): Set<String> {
        val consumes = emptySet<String>()
        consumes.plus("application/json;charset=UTF-8")
        consumes.plus("application/x-www-form-urlencoded")
        return consumes
    }

    private fun produceContentTypes(): Set<String> {
        val produces = emptySet<String>()
        produces.plus("application/json;charset=UTF-8")
        return produces
    }

    private fun requestParameter(): RequestParameter? {
        val parameterBuilder = RequestParameterBuilder()
        parameterBuilder.name("Authorization")
            .description("Access Token")
            .required(false)
            .`in`(ParameterType.HEADER)
            .build()
        return parameterBuilder.build()
    }

    @Bean
    fun commonApi(): Docket? {
        val parameters = emptyList<RequestParameter>()
        parameters.plus(requestParameter())
        return Docket(DocumentationType.SWAGGER_2)
            .globalRequestParameters(parameters)
            .consumes(consumeContentTypes())
            .produces(produceContentTypes())
            .apiInfo(apiInfo())
            .securityContexts(listOf( securityContext()))
            .securitySchemes(listOf( apiKey()))
            .select()
            .apis(RequestHandlerSelectors.any())
            .paths(PathSelectors.ant("/api/**"))
            .build()
    }

    fun apiKey(): ApiKey {
        return ApiKey("JWT", "AccessToken", "header")
    }

    fun defaultAuth(): List<SecurityReference> {
        val authorizationScope = AuthorizationScope("global", "accessEverything")
        val authorizationScopes = arrayOfNulls<AuthorizationScope>(1)
        authorizationScopes[0] = authorizationScope
        return listOf(SecurityReference("JWT", authorizationScopes))
    }

    fun securityContext(): SecurityContext {
        return SecurityContext.builder().securityReferences(defaultAuth()).build()
    }
}