package net.orbyfied.aspen.raw.nodes;

import java.util.Map;

public class RawPairNode extends RawValueNode {

    RawNode key;
    RawNode value;

    public RawPairNode() { }
    public RawPairNode(RawNode key, RawNode value) {
        this.key = key;
        this.value = value;
    }

    public RawNode getKey() {
        return key;
    }

    public RawPairNode setKey(RawNode key) {
        this.key = key;
        return this;
    }

    public RawNode getValue() {
        return value;
    }

    public RawPairNode setValue(RawNode value) {
        this.value = value;
        return this;
    }

    @Override
    protected Object toValue0() {
        return Map.entry(
                key.require(RawValueNode.class).toValue(),
                value.require(RawValueNode.class).toValue()
        );
    }

}
