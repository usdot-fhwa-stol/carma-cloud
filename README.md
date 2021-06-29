# CARMAcloud

## Documentation
CARMAcloud provides some of the infrastructure components for CARMA. It enables users to define geofences, rules of practice, replay storms to test weather-related rules of practice, as well as monitor CARMA-enabled vehicles and the messages and controls exchanged with them.

## Deployment

CARMA Cloud can be deployed using docker-compose.  Ensure that you have a publicly-accessible Linux server with ports 80 and 443 inbound from the Internet permitted in the firewall rules, and with a DNS name pointed to the server's public IP address.

Clone this repository onto the Linux server using git. Copy the `.env.example` file to `.env` and populate the environment variables as follows:


| Variable name        | Purpose                                                                              |
|----------------------|--------------------------------------------------------------------------------------|
| DNS_NAME             | This is the DNS name of the Linux server.                                            |
| CARMA_ADMIN_USER     | This will be the administrative username for the application.                        |
| CARMA_ADMIN_PASSWORD | This is the password for the administrative user.                                    |
| MAPBOX_ACCESS_TOKEN  | This is the API token for Mapbox.                                                    |
| IMPLEMENTER_EMAIL    | This is your email address, used in the process of getting Letsencrypt certificates. |



## Contribution
Welcome to the CARMA contributing guide. Please read this guide to learn about our development process, how to propose pull requests and improvements, and how to build and test your changes to this project. [CARMA Contributing Guide](Contributing.md) 

## Code of Conduct 
Please read our [CARMA Code of Conduct](Code_of_Conduct.md) which outlines our expectations for participants within the CARMA community, as well as steps to reporting unacceptable behavior. We are committed to providing a welcoming and inspiring community for all and expect our code of conduct to be honored. Anyone who violates this code of conduct may be banned from the community.

## Attribution
The development team would like to acknowledge the people who have made direct contributions to the design and code in this repository. [CARMA Attribution](ATTRIBUTION.md) 

## License
By contributing to the Federal Highway Administration (FHWA) Connected Automated Research Mobility Applications (CARMA), you agree that your contributions will be licensed under its Apache License 2.0 license. [CARMA License](<docs/License.md>) 

## Contact
Please click on the CARMA logo below to visit the Federal Highway Adminstration(FHWA) CARMA website. For more information, contact CARMA@dot.gov.

[![CARMA Image](docs/image/CARMA_icon2.png)](https://highways.dot.gov/research/research-programs/operations/CARMA)
