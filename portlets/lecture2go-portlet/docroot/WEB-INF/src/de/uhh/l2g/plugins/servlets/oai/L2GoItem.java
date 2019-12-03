package de.uhh.l2g.plugins.servlets.oai;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dspace.xoai.dataprovider.model.Item;
import org.dspace.xoai.dataprovider.model.Set;
import org.dspace.xoai.model.oaipmh.About;
import org.dspace.xoai.model.xoai.Element;
import org.dspace.xoai.model.xoai.XOAIMetadata;
import org.jsoup.Jsoup;

import com.google.common.base.Function;
import com.liferay.portal.kernel.util.PropsUtil;
import com.lyncode.builder.ListBuilder;

import de.uhh.l2g.plugins.model.Category;
import de.uhh.l2g.plugins.model.Creator;
import de.uhh.l2g.plugins.model.Lectureseries;
import de.uhh.l2g.plugins.model.License;
import de.uhh.l2g.plugins.model.Video;
import de.uhh.l2g.plugins.model.impl.VideoImpl;
import de.uhh.l2g.plugins.model.Metadata;
import de.uhh.l2g.plugins.model.Term;
import de.uhh.l2g.plugins.service.CategoryLocalServiceUtil;
import de.uhh.l2g.plugins.service.CreatorLocalServiceUtil;
import de.uhh.l2g.plugins.service.InstitutionLocalServiceUtil;
import de.uhh.l2g.plugins.service.LectureseriesLocalServiceUtil;
import de.uhh.l2g.plugins.service.LicenseLocalServiceUtil;
import de.uhh.l2g.plugins.service.MetadataLocalServiceUtil;
import de.uhh.l2g.plugins.service.TermLocalServiceUtil;
import de.uhh.l2g.plugins.service.VideoLocalServiceUtil;
import de.uhh.l2g.plugins.service.Video_CategoryLocalServiceUtil;

/**
 * The L2GoItem which holds the values of an OAI-PMH-item 
 * The metadata from Lecture2Go is collected here and mapped to the XOAI-default metadata format
 */
public class L2GoItem implements Item {

	public L2GoItem() {
	}
	
    private Map<String, Object> values = new HashMap<String, Object>();
	
    public L2GoItem with(String name, Object value) {
        values.put(name, value);
        return this;
    }
    
    public Long getVideoId() {
    	return (Long) values.get("videoId");
    }

	@Override
	public Date getDatestamp() {
		return (Date) values.get("datestamp");
	}

	@Override
	public String getIdentifier() {
		return (String) values.get("identifier");
	}

	@Override
	public List<Set> getSets() {
        List<String> list = ((List<String>) values.get("sets"));
        return new ListBuilder<String>().add(list.toArray(new String[list.size()])).build(new Function<String, org.dspace.xoai.dataprovider.model.Set>() {
            @Override
            public org.dspace.xoai.dataprovider.model.Set apply(String elem) {
                return new org.dspace.xoai.dataprovider.model.Set(elem);
            }
        });
	}

	@Override
	public boolean isDeleted() {
        return (Boolean) values.get("deleted");
	}

	@Override
	public List<About> getAbout() {
		// this is an optional field (see https://www.openarchives.org/OAI/openarchivesprotocol.html#Record), we do not use it
		return null;
	}
	
	@Override
	public org.dspace.xoai.model.oaipmh.Metadata getMetadata() {
		// in this method all relevant metadata for an video is collected and put into a Metadata object
		
		Long videoId = getVideoId();

		Video v = new VideoImpl();
		try {
			v = VideoLocalServiceUtil.getVideo(videoId);
		} catch (Exception e) {
			// there is a problem getting the video, no metadata can be filled, abort and return an empty object
			 return new org.dspace.xoai.model.oaipmh.Metadata(toMetadata());
		}
		
		// *** ID ***
		String id = String.valueOf(videoId);
		this.with("id", id);
		
		// *** Identifier ***
		String urlIdentifier = v.getUrl();
		this.with("urlIdentifier", urlIdentifier);
		this.with("identifierType", "URL");

		// *** Title ***
		String title = v.getTitle();
		this.with("title", title);
		
		// *** Metadata of series (if existing) ***
		if (v.getLectureseriesId() > 0 ) {
			Lectureseries lectureseries;
			try {
				lectureseries = LectureseriesLocalServiceUtil.getLectureseries(v.getLectureseriesId());
				String seriesTitle = lectureseries.getName();
				this.with("seriesTitle", seriesTitle);
				
				String seriesUrl = lectureseries.getOpenAccessURI();
				this.with("seriesUrl", seriesUrl);
			} catch (Exception e) {
				// there is a problem getting the title of the lectureseries, that's not too good but nevermind and fill the other metadata
			}
		}
		
		// *** Creators ***
		List<Creator> creators = CreatorLocalServiceUtil.getCreatorsByVideoId(v.getVideoId());
		List<Object> creatorsList = new ArrayList<Object>();
	    Map<String, Object> creatorsHashmap = new HashMap<String, Object>();
	    
		for (Creator c: creators) {
		    Map<String, Object> creator = new HashMap<String, Object>();
		    creator.put("fullName", c.getFullName());
		    creator.put("firstName", c.getFirstName());
		    if (!c.getMiddleName().isEmpty()) {
			    creator.put("middleName", c.getMiddleName());
		    }
		    creator.put("lastName", c.getLastName());
		    if (!c.getAffiliation().isEmpty()) {
			    creator.put("affiliation", c.getAffiliation());
		    }
		    if (!c.getOrcidId().isEmpty()) {
			    creator.put("orcidId", c.getOrcidId());
		    }
		    creatorsList.add(creator);
		}
	    this.with("creators", creatorsList);

		
		// *** GenerationDate ***
		// parse generation date
		SimpleDateFormat parseFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
		Date generationDate;
		try {
			generationDate = parseFormat.parse(v.getGenerationDate());
			// format generation date
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
			String generationDateString = format.format(generationDate);

			this.with("generationDate", generationDateString);
		} catch (ParseException e) {
			// the generation data can not be paresd, that's not too good but nevermind and fill the other metadata
		}
		
		// *** UploadDate ***
		Date uploadDate;
		uploadDate = v.getUploadDate();
		// format generation date
		if (v.getUploadDate() != null) {
			SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
			String uploadDateString = format.format(uploadDate);
		
			this.with("uploadDate", uploadDateString);
		}
		
		// *** Tags ***
		String[] tags = v.getTags().split(";|,");
		if (tags.length>1) {
			List<String> tagsList = new ArrayList<String>();
			for (String tag: tags) {
				tagsList.add(tag.trim());
			}
			this.with("tags", tagsList);
		}
	
		// *** ContainerFormat ***
		String containerFormat = v.getContainerFormat();
		this.with("containerFormat", containerFormat);
		
		// *** Publisher ***
		String rootInstitution;
		try {
			rootInstitution = InstitutionLocalServiceUtil.getRoot().getName();
			this.with("publisher", rootInstitution);
		} catch (Exception e) {
			// there is a problem getting the rootInstition/ publisher, that's not too good but nevermind and fill the other metadata
		} 
		
		
		// *** Language ***
		Metadata metadata = null;
		try {
			metadata = MetadataLocalServiceUtil.getMetadata(v.getMetadataId());
			String language = metadata.getLanguage();
			this.with("language", language);
		} catch (Exception e) {
			// there is a problem getting the metadata, that's not too good but nevermind and fill the other metadata
		} 
		
		
		// *** Contributor ***
		// contributor - datamanager
		if (PropsUtil.contains("lecture2go.oaipmh.datamanager")) {
			this.with("dataManager", PropsUtil.get("lecture2go.oaipmh.datamanager"));
		}
		
		// contributor - producer
		// the producing institution - we use the puslisher field for now, this must be handled better
		String producer;
		try {
			producer = metadata.getPublisher();
			if (!producer.isEmpty()) {
				this.with("producer", producer);
			}
		} catch (Exception e) {
			// there is a problem getting the producer, that's not too good but nevermind and fill the other metadata
		} 
		
		// *** Description ***
		// remove all html tags
		
		String description = metadata.getDescription();

		if (description.isEmpty()) {
			// try to get the description of the series
			if (v.getLectureseriesId() > 0 ) {
				Lectureseries lectureseries;
				try {
					lectureseries = LectureseriesLocalServiceUtil.getLectureseries(v.getLectureseriesId());
					description = lectureseries.getLongDesc();
				} catch (Exception e) {
					// there is a problem getting the description of the lectureseries, that's not too good but nevermind and fill the other metadata
				}
			}
		}
	
		if (!description.isEmpty()) {
			description = Jsoup.parse(description).wholeText().replaceAll("[\\r]", "");
			this.with("description", description);
		}

		
		// *** Term ***
		try {
			Term term = TermLocalServiceUtil.getTerm(v.getTermId());
			if (!term.getYear().isEmpty()) {
				String termFullName = term.getFullName();
				this.with("term", termFullName);
			}
		} catch (Exception e) {
			// there is a problem getting the term, that's not too good but nevermind and fill the other metadata
		}
		
		// *** Category ***
		try {
			Long categoryId = Video_CategoryLocalServiceUtil.getByVideo(v.getVideoId()).get(0).getCategoryId();
			Category category = CategoryLocalServiceUtil.getCategory(categoryId);
			String categoryName = category.getName();
			this.with("category", categoryName);
		} catch (Exception e) {
			// there is a problem getting the category, that's not too good but nevermind and fill the other metadata
		}
		
		// *** Duration ***
		String duration = v.getDuration().trim();
		this.with("duration", duration);
		
		// *** License ***
		License license;
		try {
			license = LicenseLocalServiceUtil.getLicense(v.getLicenseId());

			this.with("licenseFullName", license.getFullName());
			this.with("licenseShortIdentifier", license.getShortIdentifier());
			this.with("licenseUrl", license.getUrl());
			if (!license.getSchemeUrl().isEmpty()) {
				this.with("licenseSchemeUrl", license.getSchemeUrl());
			}
			if (!license.getSchemeName().isEmpty()) {
				this.with("licenseSchemeName", license.getSchemeName());
			}
		} catch (Exception e) {
			// there is a problem getting the license, that's not too good but nevermind and fill the other metadata
		}

        return new org.dspace.xoai.model.oaipmh.Metadata(toMetadata());
	}
	
	/**
	 * Here the item is mapped to the generic XOAI metadata
	 * @return the data in XOAI-metadata-format
	 */
	private XOAIMetadata toMetadata() {
        XOAIMetadata builder = new XOAIMetadata();
       
		for (String key : values.keySet()) {
			Element elementBuilder = buildElement(key, values.get(key));
			builder.withElement(elementBuilder);
		}

		return builder;
   
    }
	
	/**
	 * Handles the xml element building depending on the object type
	 * Uses recursion to traverse lists and hashmaps to create sub-elements
	 * @param key the key of the hashmap
	 * @param value the value of the hashmap
	 * @return the element (may contain sub-elements)
	 */
	private Element buildElement(String key, Object value) {
        Element elementBuilder = new Element(key);
        // Strings are simple
        if (value instanceof String)
            elementBuilder.withField(key, (String) value);
        // Dates are simple
        else if (value instanceof Date)
            elementBuilder.withField(key, ((Date) value).toString());
        // Lists are traversed recursive
        else if (value instanceof List) {
        	List<Object> obj = (List<Object>) value;
        	for (Object o: obj) {
        		Element childElement = buildElement(key, o);
        		elementBuilder.withElement(childElement);
        	}
        }
        // Hashmaps are traversed recursive
        else if (value instanceof HashMap) {
    		for (String k : ((Map<String, Object>) value).keySet()) {
				Element childElement = buildElement(k, ((Map<String, Object>) value).get(k));
	        	elementBuilder.withElement(childElement);
    		}
        }
    
		return elementBuilder;
	}

}
