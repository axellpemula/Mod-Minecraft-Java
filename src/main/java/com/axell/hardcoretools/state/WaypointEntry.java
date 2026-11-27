package com.axell.hardcoretools.state;

public class WaypointEntry {
	public String id = "";
	public String name = "Waypoint";
	public String dimension = "minecraft:overworld";
	public double x;
	public double y;
	public double z;
	public float yaw;
	public float pitch;
	public int color = 0xFFDC1E36;

	public WaypointEntry() {
	}

	public WaypointEntry(String id, String name, String dimension, double x, double y, double z, float yaw, float pitch, int color) {
		this.id = id;
		this.name = name;
		this.dimension = dimension;
		this.x = x;
		this.y = y;
		this.z = z;
		this.yaw = yaw;
		this.pitch = pitch;
		this.color = color;
	}
}
