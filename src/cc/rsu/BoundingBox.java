package cc.rsu;

public class BoundingBox {
	// Lower left
	private double x1, y1;
	// upper right
	private double x2, y2;

	public BoundingBox(double x1, double y1, double x2, double y2) {
		this.x1 = Math.min(x1, x2);
		this.x2 = Math.max(x1, x2);
		this.y1 = Math.min(y1, y2);
		this.y2 = Math.max(y1, y2);
	}

	public BoundingBox(Point p, Point q) {
		this(p.x, p.y, q.x, q.y);
	}

	/**
	 * Check if two bounding boxes intersect
	 * 
	 * @param BoundingBox
	 * @return true/false
	 */
	public boolean intersects(BoundingBox s) {
		BoundingBox r = this;
		return (r.x2 >= s.x1 && r.y2 >= s.y1 && s.x2 >= r.x1 && s.y2 >= r.y1);
	}

	@Override
	public String toString() {
		return "BoundingBox [x1=" + x1 + ", y1=" + y1 + ", x2=" + x2 + ", y2=" + y2 + "]";
	}

}
