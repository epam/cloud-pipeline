-- A node pool is an ACL-secured entity, and from now on its ACL identity is created along with it. Pools that already
-- exist get theirs here, owned by the pool's owner: left without one, the first edit or autoscaler resize would create
-- it on the spot, owned by whoever is signed in - and the autoscaler runs with nobody signed in at all.
-- Written the way the ACL service writes one: an upper-case principal SID, entries inheriting. The pool id is compared
-- as text and assigned as it is, so the same statement holds whether an object identity is a number or, since the
-- Spring Security 6 schema, text.
INSERT INTO pipeline.acl_class (class)
SELECT 'com.epam.pipeline.entity.cluster.pool.NodePool'
WHERE NOT EXISTS (
  SELECT 1 FROM pipeline.acl_class WHERE class = 'com.epam.pipeline.entity.cluster.pool.NodePool');

INSERT INTO pipeline.acl_sid (principal, sid)
SELECT DISTINCT TRUE, upper(pool.owner)
FROM pipeline.node_pool pool
WHERE pool.owner IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM pipeline.acl_sid existing WHERE existing.principal AND existing.sid = upper(pool.owner));

INSERT INTO pipeline.acl_object_identity (object_id_class, object_id_identity, owner_sid, entries_inheriting)
SELECT pool_class.id, pool.id, owner_sid.id, TRUE
FROM pipeline.node_pool pool
  JOIN pipeline.acl_class pool_class ON pool_class.class = 'com.epam.pipeline.entity.cluster.pool.NodePool'
  JOIN pipeline.acl_sid owner_sid ON owner_sid.principal AND owner_sid.sid = upper(pool.owner)
WHERE NOT EXISTS (
  SELECT 1 FROM pipeline.acl_object_identity existing
  WHERE existing.object_id_class = pool_class.id AND existing.object_id_identity::text = pool.id::text);
