#!/usr/bin/env bash

sbt clean compile scalastyle coverage test dependencyUpdates coverageOff coverageReport
