package net.orbyfied.aspen.properties;

import net.orbyfied.aspen.Property;
import net.orbyfied.aspen.exception.PropertyExceptions;
import net.orbyfied.aspen.raw.nodes.RawNode;
import net.orbyfied.aspen.raw.nodes.RawScalarNode;

public class NumberProperty<T extends Number> extends Property<T, Number> {

    enum PrimitiveNumberType {
        FLOAT(Double.class),
        INT(Long.class)

        ;

        Class<?> klass;
        PrimitiveNumberType(Class<?> klass) {
            this.klass = klass;
        }
    }

    /**
     * Get the primitive number class.
     *
     * @param klass The complex class.
     * @return The primitive type.
     */
    public static PrimitiveNumberType getPrimitiveNumberType(Class<? extends Number> klass) {
        if (
                klass == Double.class ||
                        klass == Float.class
        ) return PrimitiveNumberType.FLOAT;

        if (
                klass == Long.class ||
                        klass == Integer.class ||
                        klass == Short.class ||
                        klass == Byte.class
        ) return PrimitiveNumberType.INT;

        throw new IllegalArgumentException();
    }

    public static <T extends Number> Builder<T, Number, NumberProperty<T>> builder(String name, Class<T> tClass) {
        PrimitiveNumberType numberType = getPrimitiveNumberType(tClass);
        return new Builder<>(name, tClass, numberType.klass, () -> new NumberProperty<>(numberType));
    }

    final PrimitiveNumberType numberType;

    NumberProperty(PrimitiveNumberType numberType) {
        this.numberType = numberType;
    }

    @Override
    protected RawNode emitValue0(T value) {
        if (value == null)
            return RawScalarNode.nullNode();
        return new RawScalarNode<>(switch (numberType) {
            case INT -> value.intValue();
            case FLOAT -> value.floatValue();
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    protected T loadValue0(RawNode node) {
        if (!(node instanceof RawScalarNode<?> scalarNode))
            return PropertyExceptions.failValueError("Expected scalar node, got " + node.getClass().getSimpleName());
        if (scalarNode.getValue() == null)
            return null;
        return switch (numberType) {
            case FLOAT -> (T) Double.valueOf(((Number)scalarNode.getValue()).doubleValue());
            case INT -> (T) Long.valueOf(((Number)scalarNode.getValue()).longValue());
        };
    }

}
