CREATE UNIQUE INDEX uk_users_email_lower
    ON public.users (lower(email));
