package net.orbyfied.aspen;

import net.orbyfied.aspen.context.PropertyContext;
import net.orbyfied.aspen.exception.PropertyExceptions;
import net.orbyfied.aspen.exception.PropertyLoadException;
import net.orbyfied.aspen.raw.nodes.RawNode;
import net.orbyfied.aspen.raw.nodes.RawScalarNode;
import net.orbyfied.aspen.raw.nodes.RawUndefinedNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A property associated with a key in a
 * schema. This can be on an object or
 * on a configuration section.
 *
 * This holds general settings that can
 * be applied to any key-value pair stored,
 * like comments.
 *
 * For storing more complex types/special,
 * enum-like types like Minecraft blocks there
 * is a value type T and a primitive type P.
 *
 * The implementation of special properties
 * should override the {@link #valueFromPrimitive(Object)}
 * and {@link #valueToPrimitive(Object)} to
 * properly convert.
 *
 * @param <T> The value/complex type.
 * @param <P> The primitive type.
 *
 * @author orbyfied
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class Property<T, P> implements BaseRepresentable {

    /**
     * Builder classifier for typed properties, which need
     * to be mappable to special property types.
     */
    public interface TypedBuilder {

    }

    /** Builder Class */
    public static class Builder<T, P, R extends Property<T, P>> {

        /* properties */
        protected final String name;
        protected final Class<T> complexType;
        protected final Class<P> primitiveType;

        protected ConfigurationProvider provider;

        protected Consumer<RawNode> commenter;
        protected Accessor<T> accessor;

        protected List<PropertyComponent> components = new ArrayList<>();

        protected Supplier<T> defaultValueSupplier;

        protected boolean shared = false;

        // the instance factory
        private final Supplier<R> factory;

        public Builder(String name, Class complexType, Class primitiveType,
                       Supplier<R> factory) {
            this.name = name;
            this.complexType = complexType;
            this.primitiveType = primitiveType;
            this.factory = factory;
        }

        // creates a new anonymous builder
        // for embedded values
        public static <T, P, R extends Property<T, P>> Builder<T, P, R> newAnonymous(
                Class<T> tClass, Class<P> pClass, Supplier<R> supplier
        ) {
            return new Builder<>("<anonymous>", tClass, pClass, supplier);
        }

        /* Properties */

        public Builder<T, P, R> with(PropertyComponent component) {
            components.add(component);
            return this;
        }

        public <C extends PropertyComponent> Builder<T, P, R> with(C component, Consumer<C> consumer) {
            with(component);
            consumer.accept(component);
            return this;
        }

        public Builder<T, P, R> provider(ConfigurationProvider provider) {
            this.provider = provider;
            return this;
        }

        public Builder<T, P, R> commenter(Consumer<RawNode> commenter) {
            this.commenter = commenter;
            return this;
        }

        public Builder<T, P, R> accessor(Accessor<T> accessor) {
            this.accessor = accessor;
            return this;
        }

        public Builder<T, P, R> shared() {
            this.shared = true;
            return this;
        }

        public Builder<T, P, R> defaultValue(Supplier<T> supplier) {
            this.defaultValueSupplier = supplier;
            return this;
        }

        public Builder<T, P, R> defaultValue(T val) {
            return defaultValue(() -> val);
        }

        public R build() {
            // create instance
            R property = factory.get();

            // set properties
            property.provider = provider;
            property.name = name;
            property.primitiveType = primitiveType;
            property.complexType = complexType;
            if (property.accessor == null) property.accessor = accessor;
            if (property.commenter == null) property.commenter = commenter;

            // create actual accessor
            if (shared)
                property.accessor = Accessor.sharedMutable();
            property.actualAccessor = Accessor.defaulted(Accessor.dynamic(() -> property.accessor), defaultValueSupplier);

            // add components
            if (!components.isEmpty()) {
                property.componentMap = new HashMap<>();
                for (PropertyComponent component : components) {
                    Class<?> kl = component.getClass();
                    PropertyComponent in = property.componentMap.get(kl);
                    if (in != null) {
                        PropertyComponent.Pipeline pipeline;
                        if (in instanceof PropertyComponent.Pipeline)
                            pipeline = (PropertyComponent.Pipeline) in;
                        else {
                            pipeline = PropertyComponent.pipeline();
                            pipeline.with(in).with(component);
                        }

                        property.componentMap.put(kl, pipeline);
                    } else {
                        property.componentMap.put(kl, component);
                    }
                }
            }

            // check conversions between primitive
            // and complex types
            if (property.getClass() == Property.class /* no special impl */) {
                if (!complexType.isAssignableFrom(primitiveType)) {
                    throw new IllegalArgumentException("default property implementation does not support conversion between T:" +
                            complexType.getName() + " -> P:" + primitiveType.getName());
                }
            }

            property.init();

            return property;
        }

    }

    //////////////////////////////////////////

    // the configuration provider
    protected ConfigurationProvider provider;

    // the name of this property
    protected String name;

    // the value type of this property
    protected Class<T> complexType;

    // the primitive type to save as
    protected Class<P> primitiveType;

    // the comment in the generated file
    protected Consumer<RawNode> commenter;

    // the value accessor
    protected Accessor<T> accessor;
    protected Accessor<T> actualAccessor;

    // the schema this property is located in
    protected Schema schema;

    // the components on this property
    protected Map<Class<?>, PropertyComponent> componentMap;

    /*
        Cached Values
     */

    PropertyContext localContext;

    /**
     * Constructor for builders.
     */
    protected Property() { }

    // initializes this property
    // after the builder is done
    // configuring
    protected void init() {
        localContext = new PropertyContext(
                provider,
                null,
                schema
        );
    }

    // get the property context from
    // the given context, or use the
    // local context
    protected PropertyContext getPropertyContextOrLocal(Context context) {
        if (context == null)
            return localContext;
        if (context instanceof PropertyContext pc) {
            return pc.property(this);
        }

        return PropertyContext.from(context, this);
    }

    public Schema getSchema() {
        return schema;
    }

    public <C extends PropertyComponent> C component(Class<C> cClass) {
        return (C) componentMap.get(cClass);
    }

    public PropertyComponent.Pipeline componentPipeline(Class<?> cClass) {
        return (PropertyComponent.Pipeline) componentMap.get(cClass);
    }

    /**
     * Converts the primitive value to
     * a complex value.
     *
     * By default it will throw an error
     * if the complex and primitive types are
     * not equal, and just return the primitive
     * back.
     *
     * @param primitive The primitive value.
     * @return The value.
     */
    public T valueFromPrimitive(P primitive) {
        return (T) primitive;
    }

    /**
     * Converts the complex value to
     * a primitive value.
     *
     * By default it will throw an error
     * if the complex and primitive types are
     * not equal, and just return the primitive
     * back.
     *
     * @param value The value.
     * @return The primitive value.
     */
    public P valueToPrimitive(T value) {
        return (P) value;
    }

    /**
     * Get in the given context.
     *
     * @param context The context.
     * @return The value.
     */
    public T get(PropertyContext context) {
        return actualAccessor.get(context.property(this));
    }

    public T get() {
        return get(localContext);
    }

    /**
     * Set the value in the given context.
     *
     * @param context The context.
     * @param value The value.
     */
    public void set(PropertyContext context, T value) {
        actualAccessor.register(context.property(this), value);
    }

    public void set(T value) {
        set(localContext, value);
    }

    /**
     * Get if the property has a value set
     * in the given context.
     *
     * @param context The context.
     * @return True/false.
     */
    public boolean has(PropertyContext context) {
        return actualAccessor.has(context);
    }

    public boolean has() {
        return has(localContext);
    }

    // load value impl
    protected T loadValue0(PropertyContext context, RawNode node) {
        if (!(node instanceof RawScalarNode<?> scalarNode))
            return PropertyExceptions.failValueError("Non-scalar node given to load as primitive");
        return valueFromPrimitive((P) scalarNode.getValue());
    }

    /**
     * Load the value from the given node.
     *
     * Utilizes {@link #valueFromPrimitive(Object)}
     * to convert the saved value by default.
     *
     *
     * @param context The context.
     * @param node The node.
     * @return The value.
     */
    public T loadValue(PropertyContext context, RawNode node) {
        try {
            T val = loadValue0(context, node);

            // pass through components
            if (componentMap != null) {
                for (PropertyComponent component : componentMap.values()) {
                    component.checkLoadedValue(context, val);
                }
            }

            return val;
        } catch (Exception e) {
             throw new PropertyLoadException("<cause>", e, this, node);
        }
    }

    // emit value impl
    protected RawNode emitValue0(PropertyContext context, T value) {
        P primitiveValue = valueToPrimitive(value);
        return new RawScalarNode<>(primitiveValue);
    }

    /**
     * Emit a node for the given value.
     *
     * Utilizes {@link #valueToPrimitive(Object)}
     * to convert the value by default.
     *
     * @param context The context.
     * @param value The value.
     * @return The node.
     */
    public RawNode emitValue(PropertyContext context, T value) {
        return emitValue0(context, value);
    }

    @Override
    public RawNode emit(Context context) {
        PropertyContext c = getPropertyContextOrLocal(context);
        if (!has(c))
            return RawScalarNode.undefined();
        RawNode node = emitValue(c, get(c));
        if (commenter != null)
            commenter.accept(node);
        return node;
    }

    @Override
    public void load(Context context, RawNode node) {
        PropertyContext c = getPropertyContextOrLocal(context);
        set(c, loadValue(c, node));
    }

    public RawNode emit() {
        return emit(null);
    }

    public void load(RawNode node) {
        load(null, node);
    }

    /* Getters */

    @Override
    public BaseRepresentable getParent() {
        return null; // TODO
    }

    @Override
    public String getName() {
        return name;
    }

    public Class<T> getComplexType() {
        return complexType;
    }

    public Class<?> getPrimitiveType() {
        return primitiveType;
    }

    public Accessor<T> getAccessor() {
        return accessor;
    }

    public String getPath() {
        if (schema == null)
            return "$" + name;
        return schema.getPath() + "/" + name;
    }

    public String toPrettyString() {
        return getClass().getSimpleName() + "(" + getPath() + ")";
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(" +
                "provider=" + provider +
                ", name='" + name + '\'' +
                ", complexType=" + complexType +
                ", primitiveType=" + primitiveType +
                ", commenter=" + commenter +
                ", accessor=" + accessor +
                ", actualAccessor=" + actualAccessor +
                ", schema=" + schema +
                ", componentMap=" + componentMap +
                ", localContext=" + localContext +
                ')';
    }

}
