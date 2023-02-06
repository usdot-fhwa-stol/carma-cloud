package cc.rsu;

import java.time.Instant;
import java.util.ArrayList;

/***
 * BSMRequest class to describe the emergency vehicle current location and its
 * future path to the emergency destination.
 */
public class BSMRequest {
	// BSM hex string
	private String id;
	// BSM includes a list of GeoLocation in the route to indicate its future path
	private ArrayList<Position> route;
	private Instant last_update_at;

	/**
	 * @return BSM hex
	 */
	public String getId() {
		return id;
	}

	/*
	 * Set BSM HEX
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * @return Return list of positions that form a vehicle route
	 */
	public ArrayList<Position> getRoute() {
		return route;
	}

	/**
	 * @param update route
	 */
	public void setRoute(ArrayList<Position> route) {
		this.route = route;
	}

	/**
	 * @return last update timestamp
	 */
	public Instant getLast_update_at() {
		return last_update_at;
	}

	/**
	 * @param last_update_at
	 */
	public void setLast_update_at(Instant last_update_at) {
		this.last_update_at = last_update_at;
	}

	/**
	 * constructor
	 */
	public BSMRequest() {
		super();
		route = new ArrayList<Position>();
	}

	/**
	 * @param id    BSM Hex
	 * @param route a list of positions
	 */
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
