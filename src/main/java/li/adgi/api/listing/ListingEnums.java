package li.adgi.api.listing;

public class ListingEnums {

    public enum ListingStatus {
        DRAFT, PENDING_REVIEW, LIVE, REJECTED, PAUSED, BLOCKED, ARCHIVED
    }

    public enum ModerationStatus { PENDING, APPROVED, REJECTED }

    public enum SpaceType { GARAGE, STORAGE_UNIT }

    private ListingEnums() {}
}
