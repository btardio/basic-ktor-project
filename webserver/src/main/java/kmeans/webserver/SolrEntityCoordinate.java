package kmeans.webserver;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SolrEntityCoordinate {


	List<Coordinate> coordinates = new ArrayList<>();
	UUID schedule_uuid;
	UUID coordinate_uuid = UUID.randomUUID();

	public void setSchedule_uuid(UUID schedule_uuid) {
		this.schedule_uuid = schedule_uuid;
	}

	public UUID getCoordinate_uuid() {
		return coordinate_uuid;
	}

}
