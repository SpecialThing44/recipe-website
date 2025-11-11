package persistence.authentication

import domain.authentication.AuthUser
import persistence.cypher.Graph

class AuthUserGraph extends Graph[AuthUser] {}
