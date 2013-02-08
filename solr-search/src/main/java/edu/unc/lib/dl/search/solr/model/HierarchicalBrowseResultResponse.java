/**
 * Copyright 2008 The University of North Carolina at Chapel Hill
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.unc.lib.dl.search.solr.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;

import org.apache.solr.client.solrj.response.FacetField;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.unc.lib.dl.search.solr.model.BriefObjectMetadata;
import edu.unc.lib.dl.search.solr.model.HierarchicalFacetNode;
import edu.unc.lib.dl.search.solr.model.SearchResultResponse;
import edu.unc.lib.dl.search.solr.util.SearchSettings;
import edu.unc.lib.dl.util.ContentModelHelper;

public class HierarchicalBrowseResultResponse extends SearchResultResponse {
	protected static final Logger log = LoggerFactory.getLogger(HierarchicalBrowseResultResponse.class);

	private Map<String, Long> subcontainerCounts;
	private Set<String> matchingContainerPids = null;
	private Long rootCount;

	private static SearchSettings searchSettings;

	public HierarchicalBrowseResultResponse() {
		super();
		subcontainerCounts = new HashMap<String, Long>();
		matchingContainerPids = new HashSet<String>();
	}

	public void setSearchResultResponse(SearchResultResponse response) {
		this.setFacetFields(response.getFacetFields());
		this.setResultCount(response.getResultCount());
		this.setGeneratedQuery(response.getGeneratedQuery());
		this.setResultList(response.getResultList());
		this.setSearchState(response.getSearchState());
	}

	public Map<String, Long> getSubcontainerCounts() {
		return subcontainerCounts;
	}

	public void setSubcontainerCounts(Map<String, Long> subcontainerCounts) {
		this.subcontainerCounts = subcontainerCounts;
	}

	public void populateSubcontainerCounts(List<FacetField> facetFields) {
		subcontainerCounts = new HashMap<String, Long>();
		for (FacetField facetField : facetFields) {
			if (facetField.getValues() != null) {
				for (FacetField.Count facetValue : facetField.getValues()) {
					log.debug("Popsub|" + facetValue.getName() + ":" + facetValue.getCount());
					int index = facetValue.getName().indexOf(",");
					index = facetValue.getName().indexOf(",", index + 1);
					if (index != -1)
						subcontainerCounts.put(facetValue.getName().substring(0, index), facetValue.getCount());
				}
			}
		}
	}

	public void removeContainersWithoutContents() {
		ListIterator<BriefObjectMetadata> resultIt = this.getResultList().listIterator(this.getResultList().size());
		while (resultIt.hasPrevious()) {
			BriefObjectMetadata briefObject = resultIt.previous();
			if ((!briefObject.getCountMap().containsKey("child") || briefObject.getCountMap().get("child") == 0)
					&& briefObject.getContentModel().contains(ContentModelHelper.Model.CONTAINER.toString())) {
				if (this.matchingContainerPids != null && this.matchingContainerPids.contains(briefObject.getId())) {
					// The container was directly found by the users query, so leave it as is.
				} else {
					log.debug("Removing container " + briefObject.getId()
							+ "from hierarchical result because it has no children");
					resultIt.remove();
					// If an item is being filtered out, then decrement the counts for it and all its ancestors in
					// subcontainer counts
					if (briefObject.getAncestorPathFacet() != null
							&& briefObject.getAncestorPathFacet().getFacetNodes() != null) {
						for (HierarchicalFacetNode facetTier : briefObject.getAncestorPathFacet().getFacetNodes()) {
							String tierIdentifier = facetTier.getSearchValue();
							Long count = this.subcontainerCounts.get(tierIdentifier);
							if (count != null)
								this.subcontainerCounts.put(tierIdentifier, count - 1);
						}
					}
				}
			}
		}
	}

	public void populateMatchingContainerPids(SolrDocumentList containerList, String fieldName) {
		this.matchingContainerPids = new HashSet<String>();
		for (SolrDocument container : containerList) {
			this.matchingContainerPids.add((String) container.getFirstValue(fieldName));

		}
	}

	/**
	 * Appends item results to the end of the list and adds them as children of the root.
	 * 
	 * @param itemResults
	 */
	public void populateItemResults(List<BriefObjectMetadata> itemResults) {
		if (this.getResultList() != null && this.getResultList().size() > 0
				&& this.getResultList().get(0).getPath() != null) {
			for (HierarchicalFacetNode rootTier : this.getResultList().get(0).getPath().getFacetNodes()) {
				Long count = this.subcontainerCounts.get(rootTier.getSearchValue());
				log.debug("Adding " + itemResults.size() + " items to existing count of " + count + " for searchKey of "
						+ rootTier.getSearchValue());
				if (count == null) {
					this.subcontainerCounts.put(rootTier.getSearchValue(), (long) itemResults.size());
				} else {
					this.subcontainerCounts.put(rootTier.getSearchValue(), count + itemResults.size());
				}
			}
		}
		for (BriefObjectMetadata itemResult : itemResults) {
			this.getResultList().add(itemResult);
		}
	}

	public Long getRootCount() {
		return rootCount;
	}

	public void setRootCount(Long rootCount) {
		this.rootCount = rootCount;
	}

	public void setSearchSettings(SearchSettings searchSettings) {
		HierarchicalBrowseResultResponse.searchSettings = searchSettings;
	}

	public Set<String> getMatchingContainerPids() {
		return matchingContainerPids;
	}

	public void setMatchingContainerPids(Set<String> matchingContainerPids) {
		this.matchingContainerPids = matchingContainerPids;
	}
	
	public void setMatchingContainerPids(List<String> matchingContainerPids) {
		this.matchingContainerPids = new HashSet<String>(matchingContainerPids);
	}
}