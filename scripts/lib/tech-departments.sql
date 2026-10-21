-- 기술자료(R04) 문서의 담당 부서를 현재 departments 테이블의 ATA 매핑으로 다시 계산합니다.
-- 전달본은 "규약 부서 매핑으로 계산"한 값을 담고 있어, 부서가 바뀌면(예: FCS = ATA 27 추가) 여기서 맞춰 줍니다.
-- departments는 verify.sql 해시에 들어가지 않으므로 전달본 검증에는 영향이 없습니다.
-- 부서 시드가 아직 없으면(빈 테이블) 기존 값을 지우지 않도록 아무것도 하지 않습니다.
WITH computed AS (
    SELECT d.document_id,
           array(SELECT DISTINCT dep.code::text
                 FROM public.departments dep, unnest(string_to_array(dep.ata_chapters, ',')) ch
                 WHERE dep.active AND trim(ch) = ANY (d.ata_chapters::text[])
                 ORDER BY 1) AS departments
    FROM public.tech_documents d
)
UPDATE public.tech_documents d
SET departments = c.departments
FROM computed c
WHERE d.document_id = c.document_id
  AND d.departments IS DISTINCT FROM c.departments
  AND EXISTS (SELECT 1 FROM public.departments);
