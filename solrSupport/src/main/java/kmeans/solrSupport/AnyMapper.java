package kmeans.solrSupport;

import com.fasterxml.jackson.annotation.JsonAnyGetter;

import java.util.Map;

public class AnyMapper {
	private Map<String, String> properties;

	@JsonAnyGetter
	public Map<String, String> getProperties() {
		return properties;
	}

}
