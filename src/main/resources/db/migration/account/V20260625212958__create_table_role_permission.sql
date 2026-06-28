CREATE TABLE role_permission (
    role_code VARCHAR(4) REFERENCES role(code) ON DELETE CASCADE ON UPDATE CASCADE NOT NULL,
    permission_code VARCHAR(256) NOT NULL,
    target VARCHAR(128),
    PRIMARY KEY (role_code, permission_code)
);
