CREATE TABLE role_permission (
    role_code VARCHAR(4) REFERENCES role(code) ON DELETE CASCADE ON UPDATE CASCADE NOT NULL,
    -- код права из com.pixplaze.api.ext.data.auth.Permissions (<домен>.<ресурс>.<действие>)
    permission_code VARCHAR(256) NOT NULL,
    PRIMARY KEY (role_code, permission_code)
);
