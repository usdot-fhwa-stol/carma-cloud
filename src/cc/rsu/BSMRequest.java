package cc.rsu;

import java.time.Instant;
import java.util.ArrayList;

public class BSMRequest {
	//BSM hex string
	private String id;
	//BSM includes a list of GeoLocation in the route to indicate its future path
	private ArrayList<Position> route;
	private Instant last_update_at;
	
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public ArrayList<Position> getRoute() {
		return route;
	}

	public void setRoute(ArrayList<Position> route) {
		this.route = route;
	}
	
	
	public Instant getLast_update_at() {
		return last_update_at;
	}

	public void setLast_update_at(Instant last_update_at) {
		this.last_update_at = last_update_at;
	}

	public BSMRequest() {
		super();
		route = new ArrayList<Position>();
	}

	public BSMRequest(String id, ArrayList<Position> route) {
		super();
		this.id = id;
		this.route = route;
	}

	@Override
	public String toString() {
		return "BSMRequest [id=" + id + ", route=" + route + ", last_update_at=" + last_update_at + "]";
	}

	
	
}
