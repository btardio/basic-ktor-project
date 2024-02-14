package kmeans.webserver;

public class RabbitMessageStartRun {
	String solrEntityScheduledRun_UUID;

	String solrEntityCoordinatesList_UUID;

	public RabbitMessageStartRun(SolrEntityScheduledRun scheduledRun, SolrEntityCoordinate coordinatesList) {

		assert(scheduledRun.getCoordinate_uuid() == coordinatesList.getCoordinate_uuid());

		this.solrEntityScheduledRun_UUID = scheduledRun.getSchedule_uuid().toString();
		this.solrEntityCoordinatesList_UUID = coordinatesList.getCoordinate_uuid().toString();
	}

	public String getSolrEntityScheduledRun_UUID() {
		return solrEntityScheduledRun_UUID;
	}

	public void setSolrEntityScheduledRun_UUID(String solrEntityScheduledRun_UUID) {
		this.solrEntityScheduledRun_UUID = solrEntityScheduledRun_UUID;
	}

	public String getSolrEntityCoordinatesList_UUID() {
		return solrEntityCoordinatesList_UUID;
	}

	public void setSolrEntityCoordinatesList_UUID(String solrEntityCoordinatesList_UUID) {
		this.solrEntityCoordinatesList_UUID = solrEntityCoordinatesList_UUID;
	}
}
