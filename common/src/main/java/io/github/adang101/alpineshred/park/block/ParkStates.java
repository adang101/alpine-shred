package io.github.adang101.alpineshred.park.block;

import io.github.adang101.alpineshred.park.Facing;
import io.github.adang101.alpineshred.park.RailShape;
import io.github.adang101.alpineshred.park.RampSlope;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import java.util.Locale;

/** Block state properties of the park blocks (design doc sections 10 and 11). */
public final class ParkStates {
    private ParkStates() {
    }

    public enum RailShapeProp implements StringRepresentable {
        FLAT(RailShape.FLAT), RISE_A(RailShape.RISE_A), RISE_B(RailShape.RISE_B), CURVE(RailShape.CURVE);

        public final RailShape shape;

        RailShapeProp(RailShape shape) {
            this.shape = shape;
        }

        public static RailShapeProp of(RailShape shape) {
            return values()[shape.ordinal()];
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum BoxHalf implements StringRepresentable {
        LOW, FULL;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum RampSlopeProp implements StringRepresentable {
        QUARTER(RampSlope.QUARTER), HALF(RampSlope.HALF), FULL(RampSlope.FULL);

        public final RampSlope slope;

        RampSlopeProp(RampSlope slope) {
            this.slope = slope;
        }

        public RampSlopeProp next() {
            return values()[(ordinal() + 1) % values().length];
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum PipeRadius implements StringRepresentable {
        R3(3), R5(5);

        public final int radius;

        PipeRadius(int radius) {
            this.radius = radius;
        }

        @Override
        public String getSerializedName() {
            return Integer.toString(radius);
        }
    }

    public static final EnumProperty<RailShapeProp> RAIL_SHAPE = EnumProperty.create("shape", RailShapeProp.class);
    public static final BooleanProperty CONNECTED_A = BooleanProperty.create("connected_a");
    public static final BooleanProperty CONNECTED_B = BooleanProperty.create("connected_b");
    public static final BooleanProperty LEGS = BooleanProperty.create("legs");
    public static final BooleanProperty WAXED = BooleanProperty.create("waxed");
    public static final BooleanProperty END_A = BooleanProperty.create("end_a");
    public static final BooleanProperty END_B = BooleanProperty.create("end_b");
    public static final EnumProperty<BoxHalf> BOX_HALF = EnumProperty.create("half", BoxHalf.class);
    public static final EnumProperty<RampSlopeProp> RAMP_SLOPE = EnumProperty.create("slope", RampSlopeProp.class);
    public static final IntegerProperty RAMP_STEP = IntegerProperty.create("step", 0, 3);
    public static final BooleanProperty LIP = BooleanProperty.create("lip");
    public static final EnumProperty<PipeRadius> PIPE_RADIUS = EnumProperty.create("radius", PipeRadius.class);
    public static final IntegerProperty PIPE_INDEX = IntegerProperty.create("index", 0, 9);

    public static Facing facing(Direction d) {
        return switch (d) {
            case EAST -> Facing.EAST;
            case SOUTH -> Facing.SOUTH;
            case WEST -> Facing.WEST;
            default -> Facing.NORTH;
        };
    }

    public static Direction direction(Facing f) {
        return switch (f) {
            case NORTH -> Direction.NORTH;
            case EAST -> Direction.EAST;
            case SOUTH -> Direction.SOUTH;
            case WEST -> Direction.WEST;
        };
    }
}
