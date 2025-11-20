package persistence.authentication

import domain.authentication.RefreshToken
import persistence.cypher.Graph

class RefreshTokenGraph extends Graph[RefreshToken] {}
