package net.orbyfied.aspen.context;

import net.orbyfied.aspen.ConfigurationProvider;
import net.orbyfied.aspen.Property;
import net.orbyfied.aspen.Schema;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class OptionComposeContext extends ComposeContext {

    // the option descriptors
    final String name;
    final Class<?> type;
    final AnnotatedElement element;

    // the builder
    Property.Builder builder;

    public OptionComposeContext(ConfigurationProvider provider, Operation operation, Schema schema,
                                String name, Class<?> type, AnnotatedElement element) {
        super(provider, operation, schema);
        this.name = name;
        this.type = type;
        this.element = element;
    }

    public String name() {
        return name;
    }

    public <T> Class<T> type() {
        return (Class<T>) type;
    }

    public AnnotatedElement element() {
        return element;
    }

    public OptionComposeContext builder(Property.Builder builder) {
        this.builder = builder;
        return this;
    }

    public Property.Builder builderOrNull() {
        return builder;
    }

    public <B extends Property.Builder> B builder() {
        if (builder == null)
            throw new IllegalStateException("No builder created");
        return (B) builder;
    }

    public <B extends Property.Builder> B builder(Class<B> bClass) {
        if (!bClass.isInstance(builder))
            throw new IllegalStateException("No builder of type " + bClass.getName() + " available");
        return (B) builder;
    }

    public <A extends Annotation> void processIfPresent(Class<A> aClass,
                                                        Consumer<A> consumer) {
        A annotation = element.getAnnotation(aClass);
        if (annotation != null)
            consumer.accept(annotation);
    }

}
