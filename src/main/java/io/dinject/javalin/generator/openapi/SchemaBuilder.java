package io.dinject.javalin.generator.openapi;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class SchemaBuilder {

  private final Types types;
  private final KnownTypes knownTypes;
  //private final Elements elementUtils;
  //private final ProcessingContext ctx;

  private final TypeMirror iterableType;
  private final TypeMirror mapType;

  private OpenAPI openAPI;

  public SchemaBuilder(Types types, Elements elements) {//}, ProcessingContext ctx) {
    this.types = types;
    //this.ctx = ctx;
    //this.elementUtils = elementUtils;
    this.knownTypes = new KnownTypes();
    this.iterableType = types.erasure(elements.getTypeElement("java.lang.Iterable").asType());
    this.mapType = types.erasure(elements.getTypeElement("java.util.Map").asType());
  }

  public void setOpenAPI(OpenAPI openAPI) {
    this.openAPI = openAPI;
  }

  public Content createContent(TypeMirror returnType, String mediaType) {
    Content content = new Content();
    MediaType mt = new MediaType();
    mt.setSchema(toSchema(returnType));
    content.addMediaType(mediaType, mt);
    return content;

  }

  public Schema<?> toSchema(TypeMirror type) {


    Schema<?> schema = knownTypes.createSchema(type.toString());
    if (schema != null) {
      return schema;
    }
    if (types.isAssignable(type, mapType)) {
      return buildMapSchema(type);
    }

    if (type.getKind() == TypeKind.ARRAY) {
      return buildArraySchema(type);
    }

    if (types.isAssignable(type, iterableType)) {
      return buildIterableSchema(type);
    }

    return buildObjectSchema(type);
  }

  private Schema<?> buildObjectSchema(TypeMirror type) {

    String objectSchemaKey = getObjectSchemaName(type);

    Map<String, Schema> schemaMap = schemas();
    Schema objectSchema = schemaMap.get(objectSchemaKey);
    if (objectSchema == null) {
      objectSchema = createObjectSchema(type);
      schemaMap.put(objectSchemaKey, objectSchema);
    }

    ObjectSchema obRef = new ObjectSchema();
    obRef.$ref("#/components/schemas/" + objectSchemaKey);
    return obRef;
  }

  private Schema<?> buildIterableSchema(TypeMirror type) {

    Schema<?> itemSchema = new ObjectSchema().format("unknownIterableType");

    if (type.getKind() == TypeKind.DECLARED) {
      List<? extends TypeMirror> typeArguments = ((DeclaredType) type).getTypeArguments();
      if (typeArguments.size() == 1) {
        itemSchema = toSchema(typeArguments.get(0));
      }
    }

    ArraySchema arraySchema = new ArraySchema();
    arraySchema.setItems(itemSchema);
    return arraySchema;
  }

  private Schema<?> buildArraySchema(TypeMirror type) {

    ArrayType arrayType = types.getArrayType(type);
    Schema<?> itemSchema = toSchema(arrayType.getComponentType());

    ArraySchema arraySchema = new ArraySchema();
    arraySchema.setItems(itemSchema);
    return arraySchema;
  }

  private Schema<?> buildMapSchema(TypeMirror type) {

    Schema<?> valueSchema = new ObjectSchema().format("unknownMapValueType");

    if (type.getKind() == TypeKind.DECLARED) {
      DeclaredType declaredType = (DeclaredType) type;
      List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
      if (typeArguments.size() == 2) {
        valueSchema = toSchema(typeArguments.get(1));
      }
    }

    MapSchema mapSchema = new MapSchema();
//      mapSchema.type();
//      mapSchema.set$ref();
//      mapSchema.setFormat();
    mapSchema.setAdditionalProperties(valueSchema);
    return mapSchema;
  }

  private String getObjectSchemaName(TypeMirror type) {
    String canonicalName = type.toString();
    int pos = canonicalName.lastIndexOf('.');
    if (pos > -1) {
      canonicalName = canonicalName.substring(pos + 1);
    }
    return canonicalName;
  }

  private ObjectSchema createObjectSchema(TypeMirror objectType) {

    ObjectSchema objectSchema = new ObjectSchema();

    Element element = types.asElement(objectType);
    for (VariableElement field : allFields(element)) {
      Schema<?> propSchema = toSchema(field.asType());
      // max,min,required,deprecated ?
      objectSchema.addProperties(field.getSimpleName().toString(), propSchema);
    }
    return objectSchema;
  }


  /**
   * Gather all the fields (properties) for the given bean element.
   */
  private List<VariableElement> allFields(Element element) {

    List<VariableElement> list = new ArrayList<>();
    gatherProperties(list, element);
    return list;
  }

  /**
   * Recursively gather all the fields (properties) for the given bean element.
   */
  private void gatherProperties(List<VariableElement> fields, Element element) {

    if (element == null) {
      return;
    }
    Element mappedSuper = types.asElement(((TypeElement) element).getSuperclass());
    if (mappedSuper != null && !"java.lang.Object".equals(mappedSuper.toString())) {
      gatherProperties(fields, mappedSuper);
    }
    for (VariableElement field : ElementFilter.fieldsIn(element.getEnclosedElements())) {
      if (!ignoreField(field)) {
        fields.add(field);
      }
    }
  }

  /**
   * Return the schema map creating if needed.
   */
  private Map<String, Schema> schemas() {
    Components components = components();
    Map<String, Schema> schemas = components.getSchemas();
    if (schemas == null) {
      schemas = new LinkedHashMap<>();
      components.setSchemas(schemas);
    }
    return schemas;
  }

  /**
   * Return the components creating if needed.
   */
  private Components components() {
    Components components = openAPI.getComponents();
    if (components == null) {
      components = new Components();
      openAPI.setComponents(components);
    }
    return components;
  }

  /**
   * Ignore static or transient fields.
   */
  private boolean ignoreField(VariableElement field) {
    return isStaticOrTransient(field) || isHiddenField(field);
  }

  private boolean isHiddenField(VariableElement field) {

    Hidden hidden = field.getAnnotation(Hidden.class);
    if (hidden != null) {
      return true;
    }
    for (AnnotationMirror annotationMirror : field.getAnnotationMirrors()) {
      String simpleName = annotationMirror.getAnnotationType().asElement().getSimpleName().toString();
      if ("JsonIgnore".equals(simpleName)) {
        return true;
      }
    }
    return false;
  }

  private boolean isStaticOrTransient(VariableElement field) {
    Set<Modifier> modifiers = field.getModifiers();
    return (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.TRANSIENT));
  }

}