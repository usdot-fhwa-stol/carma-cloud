output "public_dns" {
  description = "the public DNS name assigned to the instance. "
  value       = try (aws_instance.carmacloud-test.public_dns, "")
}
output "public_ip" {
  description = "the public DNS name assigned to the instance. "
  value       = try (aws_instance.carmacloud-test.public_ip, "")
}
