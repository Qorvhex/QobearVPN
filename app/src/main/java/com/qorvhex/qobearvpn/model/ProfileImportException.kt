package com.qorvhex.qobearvpn.model

class ProfileImportException(message: String, val dependency: String? = null) : IllegalArgumentException(message)
