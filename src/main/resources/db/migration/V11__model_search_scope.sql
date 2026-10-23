-- Versioned, Backend-issued search allowance. NULL keeps pre-upgrade runs exact-model only.
ALTER TABLE public.rag_request_contexts ADD COLUMN model_scope jsonb;
ALTER TABLE public.rag_request_contexts ADD CONSTRAINT model_scope_object
    CHECK (model_scope IS NULL OR jsonb_typeof(model_scope) = 'object');
CREATE OR REPLACE VIEW public.agent_run_context AS
SELECT c.run_id, c.request_id, c.context_version, c.operator_code, c.model_key,
       c.locked_filters, c.active, c.expires_at, c.model_scope
FROM public.rag_request_contexts c;
