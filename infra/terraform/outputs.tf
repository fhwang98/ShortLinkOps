output "instance_id" {
  description = "생성된 EC2 인스턴스 ID입니다."
  value       = aws_instance.app.id
}

output "public_ip" {
  description = "EC2에 연결된 Elastic IP입니다."
  value       = aws_eip.app.public_ip
}

output "public_dns" {
  description = "EC2 인스턴스의 Public DNS입니다."
  value       = aws_instance.app.public_dns
}

output "key_pair_name" {
  description = "생성된 EC2 키페어 이름입니다."
  value       = aws_key_pair.app.key_name
}

output "private_key_file" {
  description = "생성된 SSH private key 로컬 파일 경로입니다. Git에 포함하지 않습니다."
  value       = local_sensitive_file.private_key.filename
}

output "hosted_zone_id" {
  description = "생성된 Route53 Hosted Zone ID입니다."
  value       = aws_route53_zone.app.zone_id
}

output "hosted_zone_name_servers" {
  description = "도메인 등록기관에 설정할 Route53 name server 목록입니다."
  value       = aws_route53_zone.app.name_servers
}

output "root_domain_url" {
  description = "루트 도메인 접속 주소입니다."
  value       = "https://${var.domain_name}"
}

output "www_domain_url" {
  description = "www 도메인 접속 주소입니다."
  value       = local.shortlinkops_base_url
}

output "shortlinkops_base_url" {
  description = "운영 환경 SHORTLINKOPS_BASE_URL 값입니다."
  value       = local.shortlinkops_base_url
}

output "ssh_command" {
  description = "EC2 인스턴스 SSH 접속 명령입니다."
  value       = "ssh -i ${local_sensitive_file.private_key.filename} ubuntu@${aws_eip.app.public_ip}"
}
