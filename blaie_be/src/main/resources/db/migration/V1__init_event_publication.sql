create table if not exists event_publication (
    id uuid not null primary key,
    publication_date timestamp with time zone not null,
    listener_id varchar(255) not null,
    serialized_event text not null,
    event_type varchar(255) not null,
    completion_date timestamp with time zone null,
    status varchar(255) not null,
    last_resubmission_date timestamp with time zone null,
    completion_attempts integer not null
);

create index if not exists idx_event_publication_status on event_publication (status);
create index if not exists idx_event_publication_listener_id on event_publication (listener_id);
create index if not exists idx_event_publication_event_type on event_publication (event_type);
