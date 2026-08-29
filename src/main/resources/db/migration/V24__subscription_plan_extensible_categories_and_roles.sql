-- Hibernate originally created these fields as MySQL ENUM columns. That made
-- deployments fail whenever SlickHood added a legitimate plan category or role.
-- Keep enum validation in Java and use bounded strings for forward compatibility.
ALTER TABLE pms_subscription_plan
    MODIFY COLUMN plan_category VARCHAR(64) NULL,
    MODIFY COLUMN role_family VARCHAR(64) NULL;
