package io.dinject.javalin.generator.openapi;

import io.dinject.controller.MediaType;
import io.dinject.javalin.generator.MethodParam;
import io.dinject.javalin.generator.MethodReader;
import io.dinject.javalin.generator.javadoc.Javadoc;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;

/**
 * Build the OpenAPI documentation for a method.
 */
public class MethodDocBuilder {

  private final Javadoc javadoc;
  private final MethodReader methodReader;
  private final DocContext ctx;

  private final Operation operation = new Operation();

  public MethodDocBuilder(MethodReader methodReader, DocContext ctx) {
    this.methodReader = methodReader;
    this.ctx = ctx;
    this.javadoc = methodReader.getJavadoc();
  }

  public void build() {

    if (ctx.isOpenApiAvailable() && methodReader.findAnnotation(Hidden.class) != null) {
      return;
    }

    //operation.setOperationId();
    operation.setSummary(javadoc.getSummary());
    operation.setDescription(javadoc.getDescription());

    if (javadoc.isDeprecated()) {
      operation.setDeprecated(true);
    } else if (methodReader.findAnnotation(Deprecated.class) != null) {
      operation.setDeprecated(true);
    }

    PathItem pathItem = ctx.pathItem(methodReader.getFullPath());
    switch (methodReader.getWebMethod()) {
      case GET:
        pathItem.setGet(operation);
        break;
      case PUT:
        pathItem.setPut(operation);
        break;
      case POST:
        pathItem.setPost(operation);
        break;
      case DELETE:
        pathItem.setDelete(operation);
        break;
      case PATCH:
        pathItem.setPatch(operation);
        break;
    }

    for (MethodParam param : methodReader.getParams()) {
      param.buildApiDocumentation(this);
    }

    ApiResponses responses = new ApiResponses();
    operation.setResponses(responses);

    ApiResponse response = new ApiResponse();
    response.setDescription(javadoc.getReturnDescription());

    if (methodReader.isVoid()) {
      if (isEmpty(response.getDescription())) {
        response.setDescription("No content");
      }
    } else {
      final String produces = methodReader.getProduces();
      String contentMediaType = (produces == null) ? MediaType.APPLICATION_JSON : produces;
      response.setContent(ctx.createContent(methodReader.getReturnType(), contentMediaType));
    }
    responses.addApiResponse(methodReader.getStatusCode(), response);
  }

  DocContext getContext() {
    return ctx;
  }

  Javadoc getJavadoc() {
    return javadoc;
  }

  Operation getOperation() {
    return operation;
  }

  private boolean isEmpty(String value) {
    return value == null || value.isEmpty();
  }
}
